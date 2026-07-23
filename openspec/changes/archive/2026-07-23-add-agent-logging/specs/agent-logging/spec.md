## ADDED Requirements

### Requirement: Structured logger initialization
The agent SHALL initialize a structured JSON logger at startup using Go standard library `log/slog`, without introducing third-party logging dependencies.

#### Scenario: Logger initialized before serving requests
- **WHEN** agent process starts
- **THEN** agent initializes the logger before starting the HTTP server
- **AND** logger writes JSON-formatted records
- **AND** logger uses only Go 1.21 standard library (`log/slog`)

#### Scenario: Default log location
- **WHEN** `log.dir` is not set in config
- **THEN** logger writes to `/opt/deploy-agent/log/`

#### Scenario: Configurable log directory
- **WHEN** `log.dir` is set in config
- **THEN** logger writes to the configured directory
- **AND** creates the directory if it does not exist

#### Scenario: Default log level
- **WHEN** `log.level` is not set in config
- **THEN** logger uses `info` level

### Requirement: Daily log file rotation
The agent SHALL rotate log files by calendar day, producing one file per day named `agent-YYYY-MM-DD.log`.

#### Scenario: Log file name matches current date
- **WHEN** a log record is written
- **THEN** the record is appended to `agent-<local-date>.log` in the log directory

#### Scenario: Rotation across midnight
- **WHEN** a log record is written after local date changes
- **THEN** logger closes the previous day's file
- **AND** opens a new file for the current date
- **AND** subsequent writes go to the new file

#### Scenario: Rotation is safe under concurrent writes
- **WHEN** multiple goroutines write log records simultaneously across a midnight boundary
- **THEN** no log record is lost
- **AND** no partial line is written to either file

### Requirement: Log retention and cleanup
The agent SHALL automatically delete log files older than the configured retention period.

#### Scenario: Default retention period
- **WHEN** `log.max_age_days` is not set in config
- **THEN** logs older than 30 days are eligible for deletion

#### Scenario: Configurable retention
- **WHEN** `log.max_age_days` is set to N (positive integer)
- **THEN** logs older than N days are eligible for deletion

#### Scenario: Retention disabled
- **WHEN** `log.max_age_days` is set to 0
- **THEN** no log file is deleted automatically

#### Scenario: Periodic cleanup
- **WHEN** agent has been running
- **THEN** the logging module runs a cleanup scan every 24 hours
- **AND** deletes files matching `agent-*.log` whose modification time is older than the retention window

#### Scenario: Cleanup runs on startup
- **WHEN** agent starts
- **THEN** logging module performs one cleanup scan before starting the periodic timer

### Requirement: Trace field propagation via context
The agent SHALL propagate tracing fields (task_id, upload_id, request_id) through `context.Context` so that all log records emitted during a task carry the same identifiers.

#### Scenario: Task ID attached to task-scoped context
- **WHEN** a task begins execution
- **THEN** the task handler attaches `task_id` to the context
- **AND** all log records emitted within that context include the `task_id` field

#### Scenario: Executor logger derives from context
- **WHEN** `ExecuteShell` or `ExecuteScript` is called with a context containing tracing fields
- **THEN** logs emitted by the executor include those fields

#### Scenario: Missing context falls back gracefully
- **WHEN** a log record is emitted without a context (or with a context lacking tracing fields)
- **THEN** the record is written without those fields
- **AND** the agent does not panic or error

### Requirement: Deployment step logging
The agent SHALL log the start, end, and complete output of every deployment task step.

#### Scenario: Step start event
- **WHEN** a task step begins
- **THEN** agent emits a log record with event `task.step.start`
- **AND** the record includes `task_id`, `step_index`, `step_name`, `step_type`, and (if applicable) `command` or `deploy_path`

#### Scenario: Step end event
- **WHEN** a task step completes (success, failure, or timeout)
- **THEN** agent emits a log record with event `task.step.end`
- **AND** the record includes `task_id`, `step_index`, `exit_code`, `duration_ms`, `status`
- **AND** the record includes the complete stdout+stderr in the `output` field
- **AND** `output` is not truncated regardless of size

#### Scenario: Step failure log level
- **WHEN** a step fails (non-zero exit code, error, or timeout)
- **THEN** the `task.step.end` record is emitted at `error` level
- **AND** the record includes an `error` field describing the failure

#### Scenario: Task lifecycle events
- **WHEN** a task starts or ends
- **THEN** agent emits `task.start` and `task.end` records with `task_id`, `task_name`, and (for end) `status` and `duration_ms`

### Requirement: Upload operation logging
The agent SHALL log upload lifecycle events with the associated `upload_id`.

#### Scenario: Upload init logged
- **WHEN** POST /api/upload/init succeeds
- **THEN** agent emits a log record with event `upload.init`
- **AND** the record includes `upload_id`, `filename`, `size`

#### Scenario: Upload complete logged
- **WHEN** POST /api/upload/{uploadId}/complete succeeds or fails
- **THEN** agent emits a log record with event `upload.complete`
- **AND** the record includes `upload_id`, `md5_ok`, and (on failure) `error`

### Requirement: Journalctl minimal output
The agent SHALL restrict stdout output to lifecycle events only, so that `journalctl -u deploy-agent` remains concise.

#### Scenario: Lifecycle events go to stdout
- **WHEN** agent starts, stops, or encounters a fatal error
- **THEN** the event is written to stdout (visible in journalctl)

#### Scenario: First-run token printed to stdout
- **WHEN** agent starts for the first time and generates a new token
- **THEN** the token is printed to stdout exactly as before

#### Scenario: Runtime events do not go to stdout
- **WHEN** agent handles HTTP requests, executes tasks, or performs uploads during normal operation
- **THEN** the corresponding log records are written only to the log file
- **AND** stdout remains free of routine runtime chatter
