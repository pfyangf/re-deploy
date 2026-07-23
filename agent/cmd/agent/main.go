package main

import (
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/redeploy/agent/internal/api"
	"github.com/redeploy/agent/internal/config"
	"github.com/redeploy/agent/internal/logging"
)

func main() {
	// Load configuration
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to load configuration: %v\n", err)
		os.Exit(1)
	}

	// Initialize logger (file-based structured logging)
	if err := logging.Init(logging.Config{
		Dir:        cfg.Log.Dir,
		Level:      cfg.Log.Level,
		MaxAgeDays: cfg.Log.MaxAgeDays,
	}); err != nil {
		fmt.Fprintf(os.Stderr, "Failed to initialize logger: %v\n", err)
		os.Exit(1)
	}
	defer logging.Close()

	// Print token on first run (stdout only, visible via journalctl)
	if cfg.FirstRun {
		fmt.Fprintln(os.Stdout, "============================================================")
		fmt.Fprintf(os.Stdout, "Agent Token: %s\n", cfg.Token)
		fmt.Fprintln(os.Stdout, "============================================================")
		fmt.Fprintln(os.Stdout, "Please save this token and configure it in the server.")
		fmt.Fprintf(os.Stdout, "The token has been saved to: %s\n", cfg.ConfigPath)
		fmt.Fprintln(os.Stdout, "============================================================")
	}

	// Lifecycle event: startup (visible in journalctl via stdout, also in log file)
	fmt.Fprintf(os.Stdout, "Deploy Agent starting on port %d\n", cfg.Port)
	fmt.Fprintf(os.Stdout, "Runtime logs: %s/agent-YYYY-MM-DD.log\n", cfg.Log.Dir)
	slog.Info("agent starting",
		"event", "agent.start",
		"port", cfg.Port,
		"server_url", cfg.ServerURL,
		"log_dir", cfg.Log.Dir,
	)

	// Start HTTP server
	server := api.NewServer(cfg)
	go func() {
		if err := server.Start(); err != nil {
			slog.Error("http server failed", "event", "agent.fatal", "error", err.Error())
			fmt.Fprintf(os.Stderr, "Failed to start server: %v\n", err)
			os.Exit(1)
		}
	}()

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	fmt.Fprintln(os.Stdout, "Shutting down agent...")
	slog.Info("agent stopping", "event", "agent.stop")
	server.Shutdown()
}
