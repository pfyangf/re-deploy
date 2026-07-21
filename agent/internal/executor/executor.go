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
)

type Executor struct {
	dataDir string
}

func NewExecutor(dataDir string) *Executor {
	return &Executor{dataDir: dataDir}
}

func (e *Executor) ExecuteShell(command string, timeout int) (string, int, error) {
	if timeout <= 0 {
		timeout = 60 // Default 60 seconds
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeout)*time.Second)
	defer cancel()

	var cmd *exec.Cmd
	if os.Getenv("GOOS") == "windows" {
		cmd = exec.CommandContext(ctx, "cmd", "/C", command)
	} else {
		cmd = exec.CommandContext(ctx, "sh", "-c", command)
	}

	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()

	output := stdout.String() + stderr.String()

	if ctx.Err() == context.DeadlineExceeded {
		return output, -1, fmt.Errorf("command timed out after %d seconds", timeout)
	}

	exitCode := 0
	if err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			exitCode = exitErr.ExitCode()
		} else {
			return output, -1, err
		}
	}

	return strings.TrimSpace(output), exitCode, nil
}

func (e *Executor) ExecuteScript(script string, params map[string]string, timeout int) (string, int, error) {
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

	return e.ExecuteShell(fmt.Sprintf("bash %s", scriptFile), timeout)
}
