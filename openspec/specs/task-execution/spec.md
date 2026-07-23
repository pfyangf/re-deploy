## ADDED Requirements

### Requirement: Task execution via REST API
The agent SHALL execute deployment tasks via REST API.

#### Scenario: Execute task
- **WHEN** POST /api/task/execute is called with task definition and parameters
- **THEN** agent creates task execution
- **AND** returns task_id immediately

#### Scenario: Query task status
- **WHEN** GET /api/task/{task_id}/status is called
- **THEN** agent returns task execution status (pending/running/success/failed)
- **AND** returns execution logs

#### Scenario: Cancel task
- **WHEN** POST /api/task/{task_id}/cancel is called
- **THEN** agent cancels running task if possible

### Requirement: Multi-step task execution
The agent SHALL support sequential execution of multiple steps, and SHALL log each step's start, end, complete output, and exit code to the persistent log file.

#### Scenario: Execute composite task
- **WHEN** task has multiple steps defined
- **THEN** agent executes steps in order
- **AND** stops on first failure
- **AND** returns status of each step

#### Scenario: Step execution
- **WHEN** each step is executed
- **THEN** agent records step name, start time, end time, output, exit code
- **AND** agent emits structured log records for step start and step end
- **AND** step-end log record includes complete stdout+stderr in the `output` field (not truncated)
- **AND** all step-related log records include `task_id` and `step_index`

### Requirement: Shell command execution
The agent SHALL execute shell commands as task steps, and SHALL propagate the caller's `context.Context` to enable tracing field inheritance.

#### Scenario: Execute shell command
- **WHEN** step type is "shell" with command defined
- **THEN** agent executes command via system shell
- **AND** captures stdout and stderr
- **AND** returns exit code

#### Scenario: Command timeout
- **WHEN** command execution exceeds timeout
- **THEN** agent kills the process
- **AND** returns timeout error
- **AND** emits a log record at `error` level with the timeout details and any captured partial output

#### Scenario: Execution logs carry task context
- **WHEN** a shell command is executed as part of a task step
- **THEN** log records emitted by the executor include `task_id` and `step_index` derived from the context

### Requirement: Parallel deployment
The server SHALL support deploying to multiple servers simultaneously.

#### Scenario: Deploy to multiple servers
- **WHEN** deploy request includes multiple server IDs
- **THEN** server sends task to all target agents in parallel
- **AND** aggregates results from all agents
- **AND** returns combined status

#### Scenario: Partial failure handling
- **WHEN** some agents fail while others succeed
- **THEN** server reports partial failure status
- **AND** includes per-agent success/failure details

### Task step types

#### Scenario: Pre-script step
- **WHEN** step type is "pre-script"
- **THEN** agent executes script before file operations

#### Scenario: Upload step
- **WHEN** step type is "upload"
- **THEN** agent receives file from server

#### Scenario: Deploy script step
- **WHEN** step type is "deploy-script"
- **THEN** agent executes deployment script

#### Scenario: Restart step
- **WHEN** step type is "restart"
- **THEN** agent restarts target service (e.g., systemctl restart)

#### Scenario: Health check step
- **WHEN** step type is "health-check"
- **THEN** agent executes health check script
