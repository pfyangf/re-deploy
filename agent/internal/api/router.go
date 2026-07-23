package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

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
}

func (s *Server) Start() error {
	s.server = &http.Server{
		Addr:    fmt.Sprintf(":%d", s.cfg.Port),
		Handler: s.router,
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
		if token != s.cfg.Token {
			http.Error(w, `{"error":"Invalid token"}`, http.StatusUnauthorized)
			return
		}

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
