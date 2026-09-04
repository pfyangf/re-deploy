package api

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/mux"
	"github.com/redeploy/agent/internal/logging"
)

// DownloadSession 跟踪一次「agent -> server 文件拉取」会话。
// 与 UploadSession 一样使用内存 map + 周期清理（与 upload 协议完全对称）。
type DownloadSession struct {
	ID            string    `json:"id"`
	RemotePath    string    `json:"remote_path"`
	RealPath      string    `json:"real_path"` // 符号链接解析后的绝对路径
	FileSize      int64     `json:"file_size"`
	MD5           string    `json:"md5"`
	ChunkSize     int       `json:"chunk_size"`
	Chunks        int       `json:"chunks"`
	StartTime     time.Time `json:"start_time"`
	LastAccess    time.Time `json:"last_access"`
	Complete      bool      `json:"complete"`
	Cancelled     bool      `json:"cancelled"`
	mu            sync.Mutex
}

var (
	downloadSessions   = make(map[string]*DownloadSession)
	downloadSessionsMu sync.RWMutex
)

const downloadChunkSize = 5 * 1024 * 1024 // 5 MB

// startDownloadSessionCleanup 由 NewServer 调用，周期清理过期下载会话。
// 必须是 *Server 方法（而不是包级函数），因为 TTL 来自 s.cfg.Download.SessionTTL。
func (s *Server) startDownloadSessionCleanup() {
	go func() {
		ticker := time.NewTicker(5 * time.Minute)
		defer ticker.Stop()
		for range ticker.C {
			s.cleanupExpiredDownloadSessions()
		}
	}()
}

// ---- Request / Response ----

type DownloadInitRequest struct {
	RemotePath string `json:"remote_path"`
}

type DownloadInitResponse struct {
	DownloadID string `json:"download_id"`
	ChunkSize  int    `json:"chunk_size"`
	TotalBytes int64  `json:"total_bytes"`
	TotalChunks int   `json:"total_chunks"`
}

// ---- 路径白名单校验 ----

// validateRemotePath 返回 (解析后的绝对路径, 错误)。
// 校验步骤：
//  1. 路径必须为绝对路径，且不含 `..` 段、null byte 等
//  2. 符号链接解析后必须再次匹配白名单（防 symlink 攻击）
//  3. 父目录必须存在且可读
func (s *Server) validateRemotePath(input string) (string, error) {
	if input == "" {
		return "", fmt.Errorf("empty path")
	}
	if strings.ContainsRune(input, 0) {
		return "", fmt.Errorf("null byte in path")
	}
	if !filepath.IsAbs(input) {
		return "", fmt.Errorf("path must be absolute: %s", input)
	}
	// `..` 段必须在 glob 匹配前显式禁止，path.Clean 后仍可能在符号链接处绕过
	cleaned := filepath.Clean(input)
	if strings.HasPrefix(cleaned, "..") || strings.Contains(cleaned, "/../") || strings.HasSuffix(cleaned, "/..") {
		return "", fmt.Errorf("path traversal forbidden: %s", input)
	}

	// 父目录可达
	parent := filepath.Dir(cleaned)
	if _, err := os.Stat(parent); err != nil {
		return "", fmt.Errorf("parent dir not accessible: %w", err)
	}

	// 解析符号链接后再 glob
	resolved := cleaned
	if real, err := filepath.EvalSymlinks(cleaned); err == nil {
		resolved = real
	} else if !os.IsNotExist(err) {
		// 文件存在但 stat 失败（如权限），glob 阶段会再校验一次
		return "", fmt.Errorf("symlink resolve failed: %w", err)
	}

	// 匹配白名单（支持 `*` 单段通配 与 `**` 跨任意层级目录）
	pattern := ""
	for _, p := range s.cfg.Download.AllowedPaths {
		if globMatch(p, resolved) {
			pattern = p
			break
		}
	}
	if pattern == "" {
		return "", fmt.Errorf("path not in whitelist: %s", resolved)
	}
	return resolved, nil
}

// globMatch 把 glob 模式转成正则后匹配绝对路径：
//   - `**` 匹配任意字符（含路径分隔符 `/`，可跨多级目录）
//   - `*`  匹配除 `/` 外的任意字符序列（单段内）
//   - `?`  匹配除 `/` 外的单个字符
//
// 路径均为 Linux 风格（agent 跑在目标 Linux 服务器上），分隔符固定按 `/` 处理。
func globMatch(pattern, name string) bool {
	var sb strings.Builder
	sb.WriteString("^")
	for i := 0; i < len(pattern); i++ {
		c := pattern[i]
		switch c {
		case '*':
			if i+1 < len(pattern) && pattern[i+1] == '*' {
				sb.WriteString(".*") // `**` 跨任意层级
				i++
			} else {
				sb.WriteString("[^/]*") // `*` 单段内
			}
		case '?':
			sb.WriteString("[^/]")
		case '.', '+', '(', ')', '|', '^', '$', '{', '}', '[', ']', '\\':
			sb.WriteByte('\\')
			sb.WriteByte(c)
		default:
			sb.WriteByte(c)
		}
	}
	sb.WriteString("$")
	re, err := regexp.Compile(sb.String())
	if err != nil {
		// 退回到 filepath.Match（语义较弱但不会误放行）
		ok, _ := filepath.Match(pattern, name)
		return ok
	}
	return re.MatchString(name)
}

// ---- Handlers ----

func (s *Server) downloadInitHandler(w http.ResponseWriter, r *http.Request) {
	var req DownloadInitRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	if !s.cfg.Download.Enabled {
		writeError(w, http.StatusForbidden, "file download is disabled by agent config")
		return
	}

	resolved, err := s.validateRemotePath(req.RemotePath)
	if err != nil {
		writeError(w, http.StatusForbidden, err.Error())
		return
	}

	info, err := os.Stat(resolved)
	if err != nil {
		if os.IsNotExist(err) {
			writeError(w, http.StatusNotFound, "file not found: "+req.RemotePath)
			return
		}
		writeError(w, http.StatusInternalServerError, "stat failed: "+err.Error())
		return
	}
	if info.IsDir() {
		writeError(w, http.StatusBadRequest, "cannot download a directory (yet)")
		return
	}

	// 大小限制
	if s.cfg.Download.MaxFileSize > 0 && info.Size() > s.cfg.Download.MaxFileSize {
		writeError(w, http.StatusRequestEntityTooLarge,
			fmt.Sprintf("file too large: %d > %d", info.Size(), s.cfg.Download.MaxFileSize))
		return
	}

	session := &DownloadSession{
		ID:         uuid.New().String(),
		RemotePath: req.RemotePath,
		RealPath:   resolved,
		FileSize:   info.Size(),
		ChunkSize:  downloadChunkSize,
		Chunks:     int((info.Size() + int64(downloadChunkSize) - 1) / int64(downloadChunkSize)),
		StartTime:  time.Now(),
		LastAccess: time.Now(),
	}

	downloadSessionsMu.Lock()
	downloadSessions[session.ID] = session
	downloadSessionsMu.Unlock()

	logger := logging.FromContext(logging.WithDownloadID(r.Context(), session.ID))
	logger.Info("download init",
		"event", "download.init",
		"remote_path", req.RemotePath,
		"size", info.Size(),
	)

	writeJSON(w, http.StatusOK, DownloadInitResponse{
		DownloadID: session.ID,
		ChunkSize:  session.ChunkSize,
		TotalBytes: session.FileSize,
		TotalChunks: session.Chunks,
	})
}

func (s *Server) downloadChunkHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id := vars["downloadId"]
	seqStr := r.URL.Query().Get("seq")
	if seqStr == "" {
		writeError(w, http.StatusBadRequest, "missing seq query param")
		return
	}
	seq := parseInt(seqStr)
	if seq < 0 {
		writeError(w, http.StatusBadRequest, "invalid seq")
		return
	}

	downloadSessionsMu.RLock()
	session, exists := downloadSessions[id]
	downloadSessionsMu.RUnlock()
	if !exists {
		writeError(w, http.StatusNotFound, "download session not found")
		return
	}
	if session.Cancelled {
		writeError(w, http.StatusGone, "download session cancelled")
		return
	}

	session.mu.Lock()
	session.LastAccess = time.Now()
	session.mu.Unlock()

	if seq >= session.Chunks {
		writeError(w, http.StatusBadRequest, fmt.Sprintf("seq %d out of range (max %d)", seq, session.Chunks-1))
		return
	}

	f, err := os.Open(session.RealPath)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "open source failed: "+err.Error())
		return
	}
	defer f.Close()

	offset := int64(seq) * int64(session.ChunkSize)
	if _, err := f.Seek(offset, io.SeekStart); err != nil {
		writeError(w, http.StatusInternalServerError, "seek failed: "+err.Error())
		return
	}
	remaining := session.FileSize - offset
	if remaining > int64(session.ChunkSize) {
		remaining = int64(session.ChunkSize)
	}

	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Length", fmt.Sprintf("%d", remaining))
	w.Header().Set("X-Download-Seq", fmt.Sprintf("%d", seq))
	w.Header().Set("X-Download-Id", session.ID)
	w.Header().Set("X-Download-Size", fmt.Sprintf("%d", session.FileSize))

	// LimitReader 保证不读多
	_, _ = io.Copy(w, io.LimitReader(f, remaining))
}

func (s *Server) downloadStatusHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id := vars["downloadId"]

	downloadSessionsMu.RLock()
	session, exists := downloadSessions[id]
	downloadSessionsMu.RUnlock()
	if !exists {
		writeError(w, http.StatusNotFound, "download session not found")
		return
	}

	session.mu.Lock()
	defer session.mu.Unlock()
	session.LastAccess = time.Now()
	writeJSON(w, http.StatusOK, session)
}

func (s *Server) downloadCompleteHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id := vars["downloadId"]

	downloadSessionsMu.RLock()
	session, exists := downloadSessions[id]
	downloadSessionsMu.RUnlock()
	if !exists {
		writeError(w, http.StatusNotFound, "download session not found")
		return
	}

	// 计算 MD5（仅在小文件允许时；大文件提示对端走 chunk 端到端校验）
	md5Hex := ""
	if session.FileSize < 100*1024*1024 { // 100MB 内做服务端 MD5
		f, err := os.Open(session.RealPath)
		if err == nil {
			h := md5.New()
			_, _ = io.Copy(h, f)
			_ = f.Close()
			md5Hex = hex.EncodeToString(h.Sum(nil))
			session.MD5 = md5Hex
		}
	}

	session.mu.Lock()
	session.Complete = true
	session.LastAccess = time.Now()
	session.mu.Unlock()

	logger := logging.FromContext(logging.WithDownloadID(r.Context(), id))
	logger.Info("download complete",
		"event", "download.complete",
		"path", session.RealPath,
		"size", session.FileSize,
	)

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":    "complete",
		"md5":       md5Hex,
		"file_size": session.FileSize,
	})
}

func (s *Server) downloadCancelHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id := vars["downloadId"]

	downloadSessionsMu.Lock()
	session, exists := downloadSessions[id]
	if exists {
		session.Cancelled = true
	}
	delete(downloadSessions, id)
	downloadSessionsMu.Unlock()

	if !exists {
		writeError(w, http.StatusNotFound, "download session not found")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "cancelled"})
}

func (s *Server) cleanupExpiredDownloadSessions() {
	downloadSessionsMu.Lock()
	defer downloadSessionsMu.Unlock()

	ttl := time.Duration(s.cfg.Download.SessionTTL) * time.Second
	if ttl <= 0 {
		ttl = time.Hour
	}
	now := time.Now()
	for id, session := range downloadSessions {
		if now.Sub(session.StartTime) > ttl {
			delete(downloadSessions, id)
			slog.Info("download session expired",
				"event", "download.session.expire",
				"download_id", id,
			)
		}
	}
}
