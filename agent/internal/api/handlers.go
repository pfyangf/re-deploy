package api

import (
	"net/http"
	"os"
	"runtime"
	"time"
)

var startTime = time.Now()

func (s *Server) healthHandler(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{
		"status": "ok",
	})
}

func (s *Server) infoHandler(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"version":    "1.0.0",
		"hostname":   getHostname(),
		"go_version": runtime.Version(),
		"os":         runtime.GOOS,
		"arch":       runtime.GOARCH,
		"uptime":     time.Since(startTime).String(),
	})
}

func getHostname() string {
	hostname, _ := os.Hostname()
	return hostname
}
