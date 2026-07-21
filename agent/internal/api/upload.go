package api

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/mux"
)

type UploadSession struct {
	ID        string    `json:"id"`
	Filename  string    `json:"filename"`
	FileSize  int64     `json:"file_size"`
	MD5       string    `json:"md5"`
	Chunks    int       `json:"chunks"`
	Received  []int     `json:"received"`
	StartTime time.Time `json:"start_time"`
	Complete  bool      `json:"complete"`
	mu        sync.Mutex
}

var (
	uploadSessions = make(map[string]*UploadSession)
	sessionsMu     sync.RWMutex
)

func init() {
	// Cleanup expired sessions periodically
	go func() {
		ticker := time.NewTicker(10 * time.Minute)
		for range ticker.C {
			cleanupExpiredSessions()
		}
	}()
}

type UploadInitRequest struct {
	Filename string `json:"filename"`
	FileSize int64  `json:"file_size"`
	MD5      string `json:"md5"`
}

type UploadInitResponse struct {
	UploadID  string `json:"upload_id"`
	ChunkSize int    `json:"chunk_size"`
}

func (s *Server) uploadInitHandler(w http.ResponseWriter, r *http.Request) {
	var req UploadInitRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	session := &UploadSession{
		ID:        uuid.New().String(),
		Filename:  req.Filename,
		FileSize:  req.FileSize,
		MD5:       req.MD5,
		Received:  make([]int, 0),
		StartTime: time.Now(),
	}

	sessionsMu.Lock()
	uploadSessions[session.ID] = session
	sessionsMu.Unlock()

	// Create upload directory
	uploadDir := filepath.Join(s.cfg.DataDir, "uploads", session.ID)
	if err := os.MkdirAll(uploadDir, 0755); err != nil {
		writeError(w, http.StatusInternalServerError, "Failed to create upload directory")
		return
	}

	writeJSON(w, http.StatusOK, UploadInitResponse{
		UploadID:  session.ID,
		ChunkSize: 5 * 1024 * 1024, // 5MB
	})
}

func (s *Server) uploadChunkHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	uploadID := vars["uploadId"]

	sessionsMu.RLock()
	session, exists := uploadSessions[uploadID]
	sessionsMu.RUnlock()

	if !exists {
		writeError(w, http.StatusNotFound, "Upload session not found")
		return
	}

	// Parse multipart form
	if err := r.ParseMultipartForm(5 * 1024 * 1024); err != nil {
		writeError(w, http.StatusBadRequest, "Failed to parse form")
		return
	}

	file, header, err := r.FormFile("chunk")
	if err != nil {
		writeError(w, http.StatusBadRequest, "Missing chunk file")
		return
	}
	defer file.Close()

	seq := r.FormValue("seq")
	if seq == "" {
		writeError(w, http.StatusBadRequest, "Missing seq parameter")
		return
	}

	// Save chunk
	chunkPath := filepath.Join(s.cfg.DataDir, "uploads", uploadID, fmt.Sprintf("chunk_%s", seq))
	out, err := os.Create(chunkPath)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "Failed to create chunk file")
		return
	}
	defer out.Close()

	if _, err := io.Copy(out, file); err != nil {
		writeError(w, http.StatusInternalServerError, "Failed to save chunk")
		return
	}

	session.mu.Lock()
	session.Received = append(session.Received, parseInt(seq))
	session.mu.Unlock()

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"received_seq": seq,
		"filename":     header.Filename,
	})
}

func (s *Server) uploadCompleteHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	uploadID := vars["uploadId"]

	sessionsMu.RLock()
	session, exists := uploadSessions[uploadID]
	sessionsMu.RUnlock()

	if !exists {
		writeError(w, http.StatusNotFound, "Upload session not found")
		return
	}

	// Reassemble file
	uploadDir := filepath.Join(s.cfg.DataDir, "uploads", uploadID)
	finalPath := filepath.Join(s.cfg.DataDir, "artifacts", session.Filename)

	if err := os.MkdirAll(filepath.Dir(finalPath), 0755); err != nil {
		writeError(w, http.StatusInternalServerError, "Failed to create artifacts directory")
		return
	}

	if err := reassembleFile(uploadDir, finalPath, len(session.Received)); err != nil {
		writeError(w, http.StatusInternalServerError, "Failed to reassemble file")
		return
	}

	// Verify MD5
	if session.MD5 != "" {
		calculatedMD5, err := calculateFileMD5(finalPath)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "Failed to calculate MD5")
			return
		}

		if calculatedMD5 != session.MD5 {
			writeError(w, http.StatusBadRequest, "MD5 mismatch")
			return
		}
	}

	session.mu.Lock()
	session.Complete = true
	session.mu.Unlock()

	// Cleanup chunks
	os.RemoveAll(uploadDir)

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":   "complete",
		"filename": session.Filename,
		"path":     finalPath,
	})
}

func (s *Server) uploadStatusHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	uploadID := vars["uploadId"]

	sessionsMu.RLock()
	session, exists := uploadSessions[uploadID]
	sessionsMu.RUnlock()

	if !exists {
		writeError(w, http.StatusNotFound, "Upload session not found")
		return
	}

	writeJSON(w, http.StatusOK, session)
}

func reassembleFile(chunksDir, finalPath string, chunkCount int) error {
	out, err := os.Create(finalPath)
	if err != nil {
		return err
	}
	defer out.Close()

	for i := 0; i < chunkCount; i++ {
		chunkPath := filepath.Join(chunksDir, fmt.Sprintf("chunk_%d", i))
		in, err := os.Open(chunkPath)
		if err != nil {
			return err
		}

		if _, err := io.Copy(out, in); err != nil {
			in.Close()
			return err
		}
		in.Close()
	}

	return nil
}

func calculateFileMD5(filePath string) (string, error) {
	file, err := os.Open(filePath)
	if err != nil {
		return "", err
	}
	defer file.Close()

	hash := md5.New()
	if _, err := io.Copy(hash, file); err != nil {
		return "", err
	}

	return hex.EncodeToString(hash.Sum(nil)), nil
}

func cleanupExpiredSessions() {
	sessionsMu.Lock()
	defer sessionsMu.Unlock()

	for id, session := range uploadSessions {
		if time.Since(session.StartTime) > time.Hour {
			delete(uploadSessions, id)
			os.RemoveAll(filepath.Join("./data/uploads", id))
		}
	}
}

func parseInt(s string) int {
	n := 0
	fmt.Sscanf(s, "%d", &n)
	return n
}
