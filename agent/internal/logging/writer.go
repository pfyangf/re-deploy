package logging

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type DailyWriter struct {
	dir         string
	currentDate string
	file        *os.File
	mu          sync.Mutex
}

func NewDailyWriter(dir string) (*DailyWriter, error) {
	if err := os.MkdirAll(dir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create log directory %s: %w", dir, err)
	}
	w := &DailyWriter{dir: dir}
	if err := w.rotate(time.Now()); err != nil {
		return nil, err
	}
	return w, nil
}

func (w *DailyWriter) Write(p []byte) (int, error) {
	w.mu.Lock()
	defer w.mu.Unlock()

	today := time.Now().Format("2006-01-02")
	if today != w.currentDate {
		if err := w.rotate(time.Now()); err != nil {
			return 0, err
		}
	}
	return w.file.Write(p)
}

func (w *DailyWriter) rotate(now time.Time) error {
	date := now.Format("2006-01-02")
	path := filepath.Join(w.dir, fmt.Sprintf("agent-%s.log", date))
	f, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return fmt.Errorf("failed to open log file %s: %w", path, err)
	}
	if w.file != nil {
		_ = w.file.Close()
	}
	w.file = f
	w.currentDate = date
	return nil
}

func (w *DailyWriter) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.file != nil {
		err := w.file.Close()
		w.file = nil
		return err
	}
	return nil
}
