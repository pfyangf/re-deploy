package executor

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/redeploy/agent/internal/logging"
)

type Executor struct {
	dataDir string
}

func NewExecutor(dataDir string) *Executor {
	return &Executor{dataDir: dataDir}
}

func (e *Executor) ExecuteShell(ctx context.Context, command string, timeout int) (string, int, error) {
	if ctx == nil {
		ctx = context.Background()
	}
	if timeout <= 0 {
		timeout = 60 // Default 60 seconds
	}

	logger := logging.FromContext(ctx)
	logger.Debug("shell exec start", "event", "executor.shell.start", "command", command, "timeout", timeout)

	runCtx, cancel := context.WithTimeout(ctx, time.Duration(timeout)*time.Second)
	defer cancel()

	var cmd *exec.Cmd
	if os.Getenv("GOOS") == "windows" {
		cmd = exec.CommandContext(runCtx, "cmd", "/C", command)
	} else {
		cmd = exec.CommandContext(runCtx, "sh", "-c", command)
	}

	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()

	output := stdout.String() + stderr.String()

	if runCtx.Err() == context.DeadlineExceeded {
		logger.Debug("shell exec timeout", "event", "executor.shell.timeout", "timeout", timeout, "output_bytes", len(output))
		return strings.TrimSpace(output), -1, fmt.Errorf("command timed out after %d seconds", timeout)
	}

	exitCode := 0
	if err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			exitCode = exitErr.ExitCode()
		} else {
			logger.Debug("shell exec error", "event", "executor.shell.error", "error", err.Error())
			return strings.TrimSpace(output), -1, err
		}
	}

	logger.Debug("shell exec end", "event", "executor.shell.end", "exit_code", exitCode, "output_bytes", len(output))
	return strings.TrimSpace(output), exitCode, nil
}

func (e *Executor) ExecuteScript(ctx context.Context, script string, params map[string]string, timeout int) (string, int, error) {
	// Replace parameters in script
	for key, value := range params {
		script = strings.ReplaceAll(script, fmt.Sprintf("{{%s}}", key), value)
	}

	// Create temporary script file
	scriptDir := filepath.Join(e.dataDir, "scripts")
	if err := os.MkdirAll(scriptDir, 0755); err != nil {
		return "", -1, fmt.Errorf("failed to create scripts directory: %w", err)
	}

	scriptFile := filepath.Join(scriptDir, fmt.Sprintf("script_%d.sh", time.Now().UnixNano()))
	if err := os.WriteFile(scriptFile, []byte(script), 0755); err != nil {
		return "", -1, fmt.Errorf("failed to write script file: %w", err)
	}
	defer os.Remove(scriptFile)

	return e.ExecuteShell(ctx, fmt.Sprintf("bash %s", scriptFile), timeout)
}
