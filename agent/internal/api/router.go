package api

import (
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/mux"
	"github.com/redeploy/agent/internal/config"
	"github.com/redeploy/agent/internal/logging"
)

type Server struct {
	cfg    *config.Config
	router *mux.Router
	server *http.Server
}

func NewServer(cfg *config.Config) *Server {
	s := &Server{
		cfg:    cfg,
		router: mux.NewRouter(),
	}
	s.setupRoutes()
	return s
}

func (s *Server) setupRoutes() {
	// Security headers first (applies to all responses incl. 401)
	s.router.Use(s.securityHeadersMiddleware)
	// Auth middleware
	s.router.Use(s.authMiddleware)
	s.router.Use(s.loggingMiddleware)

	// Health check (no auth required)
	s.router.HandleFunc("/api/health", s.healthHandler).Methods("GET")

	// System info
	s.router.HandleFunc("/api/info", s.infoHandler).Methods("GET")

	// Upload endpoints
	s.router.HandleFunc("/api/upload/init", s.uploadInitHandler).Methods("POST")
	s.router.HandleFunc("/api/upload/{uploadId}/chunk", s.uploadChunkHandler).Methods("POST")
	s.router.HandleFunc("/api/upload/{uploadId}/complete", s.uploadCompleteHandler).Methods("POST")
	s.router.HandleFunc("/api/upload/{uploadId}/status", s.uploadStatusHandler).Methods("GET")

	// Task execution endpoints
	s.router.HandleFunc("/api/task/execute", s.taskExecuteHandler).Methods("POST")
	s.router.HandleFunc("/api/task/{taskId}/status", s.taskStatusHandler).Methods("GET")
	s.router.HandleFunc("/api/task/{taskId}/cancel", s.taskCancelHandler).Methods("POST")

	// Ensure unmatched routes (incl. "/") still get security headers.
	// router.Use() only applies to matched routes, so wrap the default 404 handler.
	s.router.NotFoundHandler = s.securityHeadersMiddleware(http.NotFoundHandler())
}

func (s *Server) Start() error {
	s.server = &http.Server{
		Addr:              fmt.Sprintf(":%d", s.cfg.Port),
		Handler:           s.router,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       60 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       120 * time.Second,
	}
	return s.server.ListenAndServe()
}

func (s *Server) Shutdown() {
	if s.server != nil {
		s.server.Close()
	}
}

func (s *Server) authMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Skip auth for health check
		if r.URL.Path == "/api/health" {
			next.ServeHTTP(w, r)
			return
		}

		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			http.Error(w, `{"error":"Missing Authorization header"}`, http.StatusUnauthorized)
			return
		}

		token := strings.TrimPrefix(authHeader, "Bearer ")
		if subtle.ConstantTimeCompare([]byte(token), []byte(s.cfg.Token)) != 1 {
			http.Error(w, `{"error":"Invalid token"}`, http.StatusUnauthorized)
			return
		}

		next.ServeHTTP(w, r)
	})
}

func (s *Server) securityHeadersMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Permissions-Policy", "geolocation=(), microphone=()")
		w.Header().Set("Cross-Origin-Embedder-Policy", "require-corp")
		w.Header().Set("Cross-Origin-Opener-Policy", "same-origin")
		w.Header().Set("Cross-Origin-Resource-Policy", "same-origin")
		w.Header().Set("Access-Control-Allow-Origin", s.cfg.CorsOrigin)
		w.Header().Set("Clear-Site-Data", `"cache"`)
		next.ServeHTTP(w, r)
	})
}

func (s *Server) loggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		reqID := uuid.New().String()
		ctx := logging.WithRequestID(r.Context(), reqID)
		logging.FromContext(ctx).Info("http request",
			"event", "http.request",
			"method", r.Method,
			"path", r.URL.Path,
			"remote", r.RemoteAddr,
		)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
