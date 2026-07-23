package cleaner

import (
	"log/slog"
	"os"
	"path/filepath"
	"time"
)

type Cleaner struct {
	dataDir       string
	retentionDays int
}

func NewCleaner(dataDir string, retentionDays int) *Cleaner {
	if retentionDays <= 0 {
		retentionDays = 7
	}
	return &Cleaner{
		dataDir:       dataDir,
		retentionDays: retentionDays,
	}
}

func (c *Cleaner) StartCleanupTask() {
	ticker := time.NewTicker(24 * time.Hour) // Run daily
	go func() {
		for range ticker.C {
			c.cleanup()
		}
	}()
}

func (c *Cleaner) cleanup() {
	slog.Info("data cleanup start", "event", "cleaner.start", "retention_days", c.retentionDays)

	// Cleanup old uploads
	cleanupDir(filepath.Join(c.dataDir, "uploads"), c.retentionDays)

	// Cleanup old artifacts
	cleanupDir(filepath.Join(c.dataDir, "artifacts"), c.retentionDays)

	// Cleanup old scripts
	cleanupDir(filepath.Join(c.dataDir, "scripts"), c.retentionDays)

	slog.Info("data cleanup done", "event", "cleaner.done")
}

func cleanupDir(dir string, retentionDays int) {
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		return
	}

	threshold := time.Now().AddDate(0, 0, -retentionDays)

	err := filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		if !info.IsDir() && info.ModTime().Before(threshold) {
			slog.Debug("cleaner deleting file", "event", "cleaner.delete", "path", path)
			if err := os.Remove(path); err != nil {
				slog.Error("cleaner delete failed", "event", "cleaner.delete.error", "path", path, "error", err.Error())
			}
		}

		return nil
	})

	if err != nil {
		slog.Error("cleaner walk failed", "event", "cleaner.walk.error", "dir", dir, "error", err.Error())
	}
}
