package api

import (
	"bytes"
	"io"
	"log/slog"
	"net/http"
	"strings"

	"github.com/redeploy/agent/internal/security"
)

const (
	payloadVersionHeader = "X-Payload-Version"
	payloadVersionV1     = "1"
	// maxEncryptedBody 加密请求体上限（GCM 仅膨胀 28B，16MiB 足够覆盖 JSON 类请求）
	maxEncryptedBody = 16 << 20
)

// payloadCryptoMiddleware 处理 v1 载荷加密，挂在 auth 之后、logging 之前。
// 无版本头走明文老协议；豁免路径即使带头也忽略；响应在加密模式下整体加密。
func (s *Server) payloadCryptoMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		version := r.Header.Get(payloadVersionHeader)

		// 豁免：健康检查与 /chunk 分片端点（multipart / 二进制流）永远明文
		if isCryptoExempt(r.URL.Path) {
			next.ServeHTTP(w, r)
			return
		}

		// 双栈：无头 = 明文协议
		if version == "" {
			next.ServeHTTP(w, r)
			return
		}
		if version != payloadVersionV1 {
			writeError(w, http.StatusBadRequest, "unsupported payload version")
			return
		}

		key := security.DeriveKey(s.cfg.Token)
		aad := []byte(r.Method + " " + r.URL.Path)

		// 解密请求体（GET / 空 body POST 允许 Content-Length=0，仅响应加密）
		// 传 nil ResponseWriter 避免超限时底层自动写 413，统一由下面返回 400
		r.Body = http.MaxBytesReader(nil, r.Body, maxEncryptedBody)
		wire, err := io.ReadAll(r.Body)
		if err != nil {
			writeError(w, http.StatusBadRequest, "invalid encrypted payload")
			return
		}
		if len(wire) > 0 {
			plain, err := security.Open(key, wire, aad)
			if err != nil {
				writeError(w, http.StatusBadRequest, "invalid encrypted payload")
				return
			}
			r.Body = io.NopCloser(bytes.NewReader(plain))
			r.ContentLength = int64(len(plain))
		}

		// 缓冲响应，返回后整体加密，保留原状态码与外层安全响应头
		rec := &responseRecorder{ResponseWriter: w}
		next.ServeHTTP(rec, r)

		status := rec.status
		if status == 0 {
			status = http.StatusOK
		}
		sealed, err := security.Seal(key, rec.buf.Bytes(), aad)
		if err != nil {
			slog.Error("payload seal failed", "event", "payload.seal.error", "error", err.Error())
			http.Error(w, `{"error":"seal failed"}`, http.StatusInternalServerError)
			return
		}
		h := w.Header()
		h.Set(payloadVersionHeader, payloadVersionV1)
		h.Set("Content-Type", "application/octet-stream")
		h.Del("Content-Length")
		w.WriteHeader(status)
		_, _ = w.Write(sealed)
	})
}

func isCryptoExempt(path string) bool {
	return path == "/api/health" || strings.HasSuffix(path, "/chunk")
}

// responseRecorder 缓冲下游响应体，仅记录状态码不提前 WriteHeader。
type responseRecorder struct {
	http.ResponseWriter
	buf    bytes.Buffer
	status int
}

func (rr *responseRecorder) WriteHeader(code int) {
	if rr.status == 0 {
		rr.status = code
	}
}

func (rr *responseRecorder) Write(b []byte) (int, error) {
	if rr.status == 0 {
		rr.status = http.StatusOK
	}
	return rr.buf.Write(b)
}
