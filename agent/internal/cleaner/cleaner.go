package cleaner

import (
	"log"
	"os"
	"path/filepath"
	"time"
)

type Cleaner struct {
	dataDir    string
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
	log.Println("Starting cleanup task...")

	// Cleanup old uploads
	cleanupDir(filepath.Join(c.dataDir, "uploads"), c.retentionDays)

	// Cleanup old artifacts
	cleanupDir(filepath.Join(c.dataDir, "artifacts"), c.retentionDays)

	// Cleanup old scripts
	cleanupDir(filepath.Join(c.dataDir, "scripts"), c.retentionDays)

	// Cleanup old logs
	cleanupDir(filepath.Join(c.dataDir, "logs"), c.retentionDays)

	log.Println("Cleanup task completed")
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
			log.Printf("Deleting old file: %s", path)
			if err := os.Remove(path); err != nil {
				log.Printf("Failed to delete file %s: %v", path, err)
			}
		}

		return nil
	})

	if err != nil {
		log.Printf("Error cleaning directory %s: %v", dir, err)
	}
}
