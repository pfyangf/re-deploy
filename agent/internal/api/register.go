package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"os"
	"time"

	"github.com/redeploy/agent/internal/config"
)

type RegisterRequest struct {
	ServerId int    `json:"serverId"`
	Hostname string `json:"hostname"`
	IP       string `json:"ip"`
	Port     int    `json:"port"`
	Token    string `json:"token"`
}

type HeartbeatRequest struct {
	Hostname string `json:"hostname"`
	IP       string `json:"ip"`
}

func RegisterWithServer(cfg *config.Config) error {
	if cfg.ServerURL == "" {
		return fmt.Errorf("server URL not configured")
	}

	hostname, err := os.Hostname()
	if err != nil {
		hostname = "unknown"
	}

	ip := getLocalIP()

	req := RegisterRequest{
		Hostname: hostname,
		IP:       ip,
		Port:     cfg.Port,
		Token:    cfg.Token,
	}

	jsonData, err := json.Marshal(req)
	if err != nil {
		return fmt.Errorf("failed to marshal register request: %w", err)
	}

	url := fmt.Sprintf("%s/api/agents/register", cfg.ServerURL)
	resp, err := http.Post(url, "application/json", bytes.NewBuffer(jsonData))
	if err != nil {
		slog.Error("agent register failed",
			"event", "agent.register.error",
			"server_url", cfg.ServerURL,
			"error", err.Error(),
		)
		return fmt.Errorf("failed to register with server: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		slog.Error("agent register failed",
			"event", "agent.register.error",
			"server_url", cfg.ServerURL,
			"status", resp.StatusCode,
		)
		return fmt.Errorf("registration failed with status: %d", resp.StatusCode)
	}

	slog.Info("agent registered",
		"event", "agent.register.ok",
		"hostname", hostname,
		"ip", ip,
		"port", cfg.Port,
	)
	return nil
}

func StartHeartbeat(cfg *config.Config) {
	hostname, _ := os.Hostname()
	ip := getLocalIP()

	ticker := time.NewTicker(30 * time.Second)
	go func() {
		for range ticker.C {
			sendHeartbeat(cfg, hostname, ip)
		}
	}()
}

func sendHeartbeat(cfg *config.Config, hostname, ip string) {
	req := HeartbeatRequest{
		Hostname: hostname,
		IP:       ip,
	}

	jsonData, err := json.Marshal(req)
	if err != nil {
		slog.Error("heartbeat marshal failed",
			"event", "agent.heartbeat.error",
			"error", err.Error(),
		)
		return
	}

	url := fmt.Sprintf("%s/api/agents/heartbeat", cfg.ServerURL)
	resp, err := http.Post(url, "application/json", bytes.NewBuffer(jsonData))
	if err != nil {
		slog.Error("heartbeat send failed",
			"event", "agent.heartbeat.error",
			"server_url", cfg.ServerURL,
			"error", err.Error(),
		)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		slog.Error("heartbeat non-ok status",
			"event", "agent.heartbeat.error",
			"server_url", cfg.ServerURL,
			"status", resp.StatusCode,
		)
	}
}

func getLocalIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "127.0.0.1"
	}

	for _, addr := range addrs {
		if ipnet, ok := addr.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
			if ipnet.IP.To4() != nil {
				return ipnet.IP.String()
			}
		}
	}

	return "127.0.0.1"
}
