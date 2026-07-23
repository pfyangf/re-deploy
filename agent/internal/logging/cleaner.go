package logging

import (
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"time"
)

func StartCleanup() {
	if cfg.MaxAgeDays <= 0 {
		return
	}
	go func() {
		runCleanup()
		ticker := time.NewTicker(24 * time.Hour)
		defer ticker.Stop()
		for range ticker.C {
			runCleanup()
		}
	}()
}

func runCleanup() {
	if cfg.MaxAgeDays <= 0 {
		return
	}
	threshold := time.Now().AddDate(0, 0, -cfg.MaxAgeDays)
	entries, err := os.ReadDir(cfg.Dir)
	if err != nil {
		slog.Error("log cleanup read dir failed", "event", "log.cleanup.error", "dir", cfg.Dir, "error", err.Error())
		return
	}
	deleted := 0
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		name := e.Name()
		if !strings.HasPrefix(name, "agent-") || !strings.HasSuffix(name, ".log") {
			continue
		}
		info, err := e.Info()
		if err != nil {
			continue
		}
		if info.ModTime().Before(threshold) {
			path := filepath.Join(cfg.Dir, name)
			if err := os.Remove(path); err != nil {
				slog.Error("log cleanup delete failed", "event", "log.cleanup.error", "path", path, "error", err.Error())
				continue
			}
			deleted++
		}
	}
	if deleted > 0 {
		slog.Info("log cleanup done", "event", "log.cleanup.done", "deleted", deleted, "max_age_days", cfg.MaxAgeDays)
	}
}
