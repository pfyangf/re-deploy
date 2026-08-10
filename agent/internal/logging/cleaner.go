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
	deleted := 0

	// 扫描 daily 日志 {log.dir}/agent-*.log
	deleted += cleanupDir(cfg.Dir, "agent-", ".log", threshold)

	// 扫描 per-task 日志 {log.dir}/tasks/*.log
	tasksDir := filepath.Join(cfg.Dir, "tasks")
	deleted += cleanupDir(tasksDir, "", ".log", threshold)

	if deleted > 0 {
		slog.Info("log cleanup done", "event", "log.cleanup.done", "deleted", deleted, "max_age_days", cfg.MaxAgeDays)
	}
}

// cleanupDir 扫描 dir 下前缀+后缀匹配的文件，删除 mtime 早于 threshold 的
func cleanupDir(dir, prefix, suffix string, threshold time.Time) int {
	entries, err := os.ReadDir(dir)
	if err != nil {
		slog.Error("log cleanup read dir failed", "event", "log.cleanup.error", "dir", dir, "error", err.Error())
		return 0
	}
	deleted := 0
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		name := e.Name()
		if prefix != "" && !strings.HasPrefix(name, prefix) {
			continue
		}
		if suffix != "" && !strings.HasSuffix(name, suffix) {
			continue
		}
		info, err := e.Info()
		if err != nil {
			continue
		}
		if info.ModTime().Before(threshold) {
			path := filepath.Join(dir, name)
			if err := os.Remove(path); err != nil {
				slog.Error("log cleanup delete failed", "event", "log.cleanup.error", "path", path, "error", err.Error())
				continue
			}
			deleted++
		}
	}
	return deleted
}
