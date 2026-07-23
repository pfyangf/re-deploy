package logging

import (
	"fmt"
	"log/slog"
	"os"
	"strings"
)

type Config struct {
	Dir         string
	Level       string
	MaxAgeDays  int
}

var (
	writer *DailyWriter
	cfg    Config
)

func Init(c Config) error {
	if c.Dir == "" {
		c.Dir = "/opt/deploy-agent/log"
	}
	if c.Level == "" {
		c.Level = "info"
	}
	cfg = c

	w, err := NewDailyWriter(c.Dir)
	if err != nil {
		return err
	}
	writer = w

	handler := slog.NewJSONHandler(w, &slog.HandlerOptions{
		Level: parseLevel(c.Level),
	})
	slog.SetDefault(slog.New(handler))

	StartCleanup()
	return nil
}

func Close() error {
	if writer != nil {
		return writer.Close()
	}
	return nil
}

func Dir() string {
	return cfg.Dir
}

func MaxAgeDays() int {
	return cfg.MaxAgeDays
}

func parseLevel(s string) slog.Level {
	switch strings.ToLower(s) {
	case "debug":
		return slog.LevelDebug
	case "info":
		return slog.LevelInfo
	case "warn", "warning":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		fmt.Fprintf(os.Stderr, "unknown log level %q, falling back to info\n", s)
		return slog.LevelInfo
	}
}
