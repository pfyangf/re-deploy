package api

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/mux"
	"github.com/redeploy/agent/internal/executor"
	"github.com/redeploy/agent/internal/logging"
)

type TaskExecution struct {
	ID        string          `json:"id"`
	TaskName  string          `json:"task_name"`
	Status    string          `json:"status"`
	Steps     []StepExecution `json:"steps"`
	StartTime time.Time       `json:"start_time"`
	EndTime   *time.Time      `json:"end_time,omitempty"`
	Error     string          `json:"error,omitempty"`
}

type StepExecution struct {
	Name      string     `json:"name"`
	Type      string     `json:"type"`
	Status    string     `json:"status"`
	StartTime time.Time  `json:"start_time"`
	EndTime   *time.Time `json:"end_time,omitempty"`
	Output    string     `json:"output"`
	ExitCode  int        `json:"exit_code"`
}

type TaskExecuteRequest struct {
	TaskName string            `json:"task_name"`
	Steps    []StepDef         `json:"steps"`
	Params   map[string]string `json:"params"`
}

type StepDef struct {
	Name       string `json:"name"`
	Type       string `json:"type"`
	Command    string `json:"command"`
	Script     string `json:"script"`
	DeployPath string `json:"deployPath"`
	Timeout    int    `json:"timeout"`
}

var (
	taskExecutions = make(map[string]*TaskExecution)
	tasksMu        sync.RWMutex
)

func (s *Server) taskExecuteHandler(w http.ResponseWriter, r *http.Request) {
	var req TaskExecuteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	execution := &TaskExecution{
		ID:        uuid.New().String(),
		TaskName:  req.TaskName,
		Status:    "running",
		StartTime: time.Now(),
		Steps:     make([]StepExecution, 0),
	}

	tasksMu.Lock()
	taskExecutions[execution.ID] = execution
	tasksMu.Unlock()

	// Derive task-scoped context from the request context (carries request_id)
	taskCtx := logging.WithTaskID(context.Background(), execution.ID)
	if reqID := logging.RequestIDFromContext(r.Context()); reqID != "" {
		taskCtx = logging.WithRequestID(taskCtx, reqID)
	}

	// Execute task in background
	go s.executeTask(taskCtx, execution, req.Steps, req.Params)

	writeJSON(w, http.StatusOK, map[string]string{
		"task_id": execution.ID,
		"status":  "running",
	})
}

func (s *Server) taskStatusHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	taskID := vars["taskId"]

	tasksMu.RLock()
	execution, exists := taskExecutions[taskID]
	tasksMu.RUnlock()

	if !exists {
		writeError(w, http.StatusNotFound, "Task not found")
		return
	}

	writeJSON(w, http.StatusOK, execution)
}

func (s *Server) taskCancelHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	taskID := vars["taskId"]

	tasksMu.Lock()
	execution, exists := taskExecutions[taskID]
	if exists && execution.Status == "running" {
		execution.Status = "cancelled"
		now := time.Now()
		execution.EndTime = &now
	}
	tasksMu.Unlock()

	if !exists {
		writeError(w, http.StatusNotFound, "Task not found")
		return
	}

	logging.FromContext(logging.WithTaskID(r.Context(), taskID)).Info(
		"task cancelled",
		"event", "task.cancel",
	)

	writeJSON(w, http.StatusOK, map[string]string{
		"task_id": taskID,
		"status":  execution.Status,
	})
}

func (s *Server) executeTask(ctx context.Context, execution *TaskExecution, steps []StepDef, params map[string]string) {
	exec := executor.NewExecutor(s.cfg.DataDir)

	// 开 per-task 日志 writer，构造 task-scoped logger（fan-out 到 daily + task 文件）
	tw, err := logging.OpenTaskLog(execution.ID)
	if err != nil {
		// 开 per-task 失败不阻塞 task 执行，仍用 default logger
		slog.Warn("failed to open per-task log, fallback to daily only",
			"event", "tasklog.open.error",
			"task_id", execution.ID,
			"error", err.Error(),
		)
	}
	if tw != nil {
		fanOut := logging.NewTaskFanOutWriter(logging.Writer(), tw)
		handler := slog.NewJSONHandler(fanOut, &slog.HandlerOptions{Level: slog.LevelInfo})
		taskLogger := slog.New(handler).With("task_id", execution.ID)
		ctx = logging.WithTaskLogger(ctx, taskLogger)
		defer logging.CloseTaskLog(execution.ID)
	}

	taskLogger := logging.FromContext(ctx)
	taskStart := time.Now()

	taskLogger.Info("task start",
		"event", "task.start",
		"task_name", execution.TaskName,
		"step_count", len(steps),
	)

	for i, step := range steps {
		// Check if task is cancelled
		tasksMu.RLock()
		if execution.Status == "cancelled" {
			tasksMu.RUnlock()
			taskLogger.Info("task cancelled mid-flight",
				"event", "task.end",
				"status", "cancelled",
				"duration_ms", time.Since(taskStart).Milliseconds(),
			)
			return
		}
		tasksMu.RUnlock()

		stepExec := StepExecution{
			Name:      step.Name,
			Type:      step.Type,
			StartTime: time.Now(),
			Status:    "running",
		}

		// Update execution with current step
		tasksMu.Lock()
		execution.Steps = append(execution.Steps, stepExec)
		tasksMu.Unlock()

		stepCtx := logging.WithStepIndex(ctx, i)
		stepLogger := logging.FromContext(stepCtx)

		startAttrs := []any{
			"event", "task.step.start",
			"step_name", step.Name,
			"step_type", step.Type,
			"timeout", step.Timeout,
		}
		if step.Command != "" {
			startAttrs = append(startAttrs, "command", step.Command)
		}
		if step.DeployPath != "" {
			startAttrs = append(startAttrs, "deploy_path", step.DeployPath)
		}
		stepLogger.Info("step start", startAttrs...)

		var output string
		var exitCode int
		var err error

		switch step.Type {
		case "shell", "command":
			output, exitCode, err = exec.ExecuteShell(stepCtx, step.Command, step.Timeout)
		case "script":
			output, exitCode, err = exec.ExecuteScript(stepCtx, step.Script, params, step.Timeout)
		case "deploy":
			// Deploy step: copy uploaded artifact to target deployment path
			artifactFilename, hasArtifact := params["artifactFilename"]
			if !hasArtifact || artifactFilename == "" {
				output = "Missing artifactFilename parameter for deploy step"
				exitCode = 1
			} else {
				// Source: ./data/artifacts/{filename}
				srcPath := filepath.Join(s.cfg.DataDir, "artifacts", artifactFilename)
				destPath := step.DeployPath
				if destPath == "" {
					output = "Missing deployPath in deploy step configuration"
					exitCode = 1
				} else {
					// Check if destination is a directory, if so append filename
					fi, errStat := os.Stat(destPath)
					if errStat == nil && fi.IsDir() {
						destPath = filepath.Join(destPath, artifactFilename)
					}
					// Copy the file
					err = copyFile(srcPath, destPath)
					if err != nil {
						output = fmt.Sprintf("Failed to deploy artifact: %v", err)
						exitCode = 1
					} else {
						output = fmt.Sprintf("Successfully deployed artifact %s to %s", artifactFilename, destPath)
						exitCode = 0
					}
				}
			}
		default:
			output = fmt.Sprintf("Unknown step type: %s", step.Type)
			exitCode = 1
		}

		now := time.Now()
		stepExec.EndTime = &now
		stepExec.Output = output
		stepExec.ExitCode = exitCode

		if err != nil {
			stepExec.Status = "failed"
			stepExec.Output = err.Error()
		} else if exitCode != 0 {
			stepExec.Status = "failed"
		} else {
			stepExec.Status = "success"
		}

		duration := time.Since(stepExec.StartTime)
		endAttrs := []any{
			"event", "task.step.end",
			"step_name", step.Name,
			"step_type", step.Type,
			"exit_code", exitCode,
			"status", stepExec.Status,
			"duration_ms", duration.Milliseconds(),
			"output", stepExec.Output,
		}
		if err != nil {
			endAttrs = append(endAttrs, "error", err.Error())
			stepLogger.Log(stepCtx, slog.LevelError, "step end", endAttrs...)
		} else if stepExec.Status == "failed" {
			stepLogger.Log(stepCtx, slog.LevelError, "step end", endAttrs...)
		} else {
			stepLogger.Info("step end", endAttrs...)
		}

		// Update step execution
		tasksMu.Lock()
		execution.Steps[i] = stepExec
		tasksMu.Unlock()

		// Stop on failure
		if stepExec.Status == "failed" {
			execution.Status = "failed"
			execution.Error = fmt.Sprintf("Step '%s' failed: %s", step.Name, stepExec.Output)
			now := time.Now()
			execution.EndTime = &now
			taskLogger.Error("task end",
				"event", "task.end",
				"status", "failed",
				"duration_ms", time.Since(taskStart).Milliseconds(),
				"error", execution.Error,
			)
			return
		}
	}

	execution.Status = "success"
	now := time.Now()
	execution.EndTime = &now
	taskLogger.Info("task end",
		"event", "task.end",
		"status", "success",
		"duration_ms", time.Since(taskStart).Milliseconds(),
	)
}

// copyFile copies a file from src to dst
func copyFile(src, dst string) error {
	srcFile, err := os.Open(src)
	if err != nil {
		return err
	}
	defer srcFile.Close()

	// Create destination directory if needed
	dstDir := filepath.Dir(dst)
	os.MkdirAll(dstDir, 0755)

	dstFile, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer dstFile.Close()

	_, err = io.Copy(dstFile, srcFile)
	return err
}

// taskLogsHandler 返回 task 的 per-task 日志文件内容（每行一个 JSON slog record）
func (s *Server) taskLogsHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	taskID := vars["taskId"]

	data, err := logging.ReadTaskLog(taskID)
	if err != nil {
		if os.IsNotExist(err) {
			writeError(w, http.StatusNotFound, "Task logs not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "Failed to read task logs: "+err.Error())
		return
	}

	w.Header().Set("Content-Type", "application/x-ndjson; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	w.Write(data)
}
