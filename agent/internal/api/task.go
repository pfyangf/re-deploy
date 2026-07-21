package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/mux"
	"github.com/redeploy/agent/internal/executor"
)

type TaskExecution struct {
	ID        string              `json:"id"`
	TaskName  string              `json:"task_name"`
	Status    string              `json:"status"`
	Steps     []StepExecution     `json:"steps"`
	StartTime time.Time           `json:"start_time"`
	EndTime   *time.Time          `json:"end_time,omitempty"`
	Error     string              `json:"error,omitempty"`
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
	TaskName string          `json:"task_name"`
	Steps    []StepDef       `json:"steps"`
	Params   map[string]string `json:"params"`
}

type StepDef struct {
	Name    string `json:"name"`
	Type    string `json:"type"`
	Command string `json:"command"`
	Script  string `json:"script"`
	Timeout int    `json:"timeout"`
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

	// Execute task in background
	go s.executeTask(execution, req.Steps, req.Params)

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

	writeJSON(w, http.StatusOK, map[string]string{
		"task_id": taskID,
		"status":  execution.Status,
	})
}

func (s *Server) executeTask(execution *TaskExecution, steps []StepDef, params map[string]string) {
	executor := executor.NewExecutor(s.cfg.DataDir)

	for i, step := range steps {
		// Check if task is cancelled
		tasksMu.RLock()
		if execution.Status == "cancelled" {
			tasksMu.RUnlock()
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

		var output string
		var exitCode int
		var err error

		switch step.Type {
		case "shell":
			output, exitCode, err = executor.ExecuteShell(step.Command, step.Timeout)
		case "script":
			output, exitCode, err = executor.ExecuteScript(step.Script, params, step.Timeout)
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
			return
		}
	}

	execution.Status = "success"
	now := time.Now()
	execution.EndTime = &now
}
