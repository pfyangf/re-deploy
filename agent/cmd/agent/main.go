package main

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/redeploy/agent/internal/api"
	"github.com/redeploy/agent/internal/config"
)

func main() {
	// Load configuration
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	// Print token on first run
	if cfg.FirstRun {
		fmt.Printf("============================================================\n")
		fmt.Printf("Agent Token: %s\n", cfg.Token)
		fmt.Printf("============================================================\n")
		fmt.Printf("Please save this token and configure it in the server.\n")
		fmt.Printf("The token has been saved to: %s\n", cfg.ConfigPath)
		fmt.Printf("============================================================\n")
	}

	// Start HTTP server
	server := api.NewServer(cfg)
	go func() {
		if err := server.Start(); err != nil {
			log.Fatalf("Failed to start server: %v", err)
		}
	}()

	log.Printf("Deploy Agent started on port %d", cfg.Port)
	log.Printf("Server URL: %s", cfg.ServerURL)

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down agent...")
	server.Shutdown()
}
