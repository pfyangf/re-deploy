package logging

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"
)

// TaskWriter 写 {log.dir}/tasks/{taskID}.log，关闭后 Write 丢弃且不 panic
type TaskWriter struct {
	file   *os.File
	mu     sync.Mutex
	closed bool
}

func newTaskWriter(dir, taskID string) (*TaskWriter, error) {
	tasksDir := filepath.Join(dir, "tasks")
	if err := os.MkdirAll(tasksDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create tasks log dir %s: %w", tasksDir, err)
	}
	path := filepath.Join(tasksDir, taskID+".log")
	f, err := os.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return nil, fmt.Errorf("failed to open task log %s: %w", path, err)
	}
	return &TaskWriter{file: f}, nil
}

func (w *TaskWriter) Write(p []byte) (int, error) {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.closed {
		return 0, fmt.Errorf("task log writer closed")
	}
	return w.file.Write(p)
}

func (w *TaskWriter) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.closed {
		return nil
	}
	w.closed = true
	return w.file.Close()
}

// taskWriters 维护 taskID -> *TaskWriter，供 OpenTaskLog/CloseTaskLog/ReadTaskLog 使用
var (
	taskWritersMu sync.Mutex
	taskWriters   = make(map[string]*TaskWriter)
)

// OpenTaskLog 打开（或复用）task 的 per-task writer 并注册到 map
func OpenTaskLog(taskID string) (*TaskWriter, error) {
	taskWritersMu.Lock()
	defer taskWritersMu.Unlock()
	if w, ok := taskWriters[taskID]; ok {
		return w, nil
	}
	w, err := newTaskWriter(cfg.Dir, taskID)
	if err != nil {
		return nil, err
	}
	taskWriters[taskID] = w
	return w, nil
}

// CloseTaskLog 关闭并注销 task 的 per-task writer
func CloseTaskLog(taskID string) {
	taskWritersMu.Lock()
	defer taskWritersMu.Unlock()
	if w, ok := taskWriters[taskID]; ok {
		_ = w.Close()
		delete(taskWriters, taskID)
	}
}

// TaskWriterOf 返回已注册的 task writer（不存在返回 nil）
func TaskWriterOf(taskID string) *TaskWriter {
	taskWritersMu.Lock()
	defer taskWritersMu.Unlock()
	return taskWriters[taskID]
}

// ReadTaskLog 读取 task 日志文件全部内容
func ReadTaskLog(taskID string) ([]byte, error) {
	path := filepath.Join(cfg.Dir, "tasks", taskID+".log")
	return os.ReadFile(path)
}

// TaskFanOutWriter 同时写 DailyWriter 和 task 的 TaskWriter
// task writer 写失败仅记 error，不阻断 daily
type TaskFanOutWriter struct {
	daily  *DailyWriter
	tw     *TaskWriter
	dailyW int // 仅用于满足 io.Writer 约定，daily 写失败时仍记 error
}

func NewTaskFanOutWriter(daily *DailyWriter, tw *TaskWriter) *TaskFanOutWriter {
	return &TaskFanOutWriter{daily: daily, tw: tw}
}

func (w *TaskFanOutWriter) Write(p []byte) (int, error) {
	n, err := w.daily.Write(p)
	if err != nil {
		return n, err
	}
	if w.tw != nil {
		if _, e := w.tw.Write(p); e != nil {
			// task 写失败不阻断 daily，仅记一次 error 到 daily
			_, _ = w.daily.Write([]byte(fmt.Sprintf(`{"event":"tasklog.write.error","error":%q}`+"\n", e.Error())))
		}
	}
	return n, nil
}

// 编译期断言
var _ io.Writer = (*TaskFanOutWriter)(nil)
var _ io.Closer = (*TaskWriter)(nil)
