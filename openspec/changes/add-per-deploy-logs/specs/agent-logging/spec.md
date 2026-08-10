## ADDED Requirements

### Requirement: Per-task log file archiving
The agent SHALL, in addition to the daily log file, write a per-task log file `tasks/{taskID}.log` under the configured log directory, capturing all log records emitted during that task's execution.

#### Scenario: Per-task file created on task start
- **WHEN** a task begins execution
- **THEN** the agent creates a file at `{log.dir}/tasks/{taskID}.log`
- **AND** all log records emitted within that task's context are appended to both the daily log file and this per-task file

#### Scenario: Per-task file closed on task end
- **WHEN** a task completes (success, failure, or cancellation)
- **THEN** the agent closes the per-task log file
- **AND** subsequent log records for other tasks do not write to the closed file

#### Scenario: Per-task file safe under concurrent tasks
- **WHEN** multiple tasks execute concurrently
- **THEN** each task writes only to its own per-task file
- **AND** no task's records leak into another task's file

#### Scenario: Write after close is discarded safely
- **WHEN** a log record is emitted for a task whose per-task file has already been closed
- **THEN** the record is not written to the closed file
- **AND** the record is still written to the daily log file
- **AND** the agent does not panic

### Requirement: Per-task log file cleanup
The agent SHALL clean up per-task log files under `{log.dir}/tasks/` using the same retention policy as daily log files.

#### Scenario: Per-task files cleaned by retention
- **WHEN** the cleanup scan runs (on startup and every 24 hours)
- **THEN** files under `{log.dir}/tasks/` matching `*.log` whose modification time is older than `log.max_age_days` are deleted

#### Scenario: Retention disabled applies to per-task files
- **WHEN** `log.max_age_days` is set to 0
- **THEN** no per-task log file is deleted automatically

### Requirement: Task logs retrieval endpoint
The agent SHALL expose an HTTP endpoint to retrieve a task's per-task log file content.

#### Scenario: Retrieve existing task logs
- **WHEN** `GET /api/task/{taskId}/logs` is called with a taskId whose per-task file exists
- **THEN** the agent returns HTTP 200 with the file content as the response body
- **AND** the content type is `application/x-ndjson` (one JSON log record per line)

#### Scenario: Task not found
- **WHEN** `GET /api/task/{taskId}/logs` is called with a taskId that has no per-task file
- **THEN** the agent returns HTTP 404

#### Scenario: Endpoint requires authentication
- **WHEN** `GET /api/task/{taskId}/logs` is called without a valid bearer token
- **THEN** the agent returns HTTP 401

## MODIFIED Requirements

### Requirement: Log retention and cleanup
The agent SHALL automatically delete log files older than the configured retention period, covering both daily log files and per-task log files.

#### Scenario: Default retention period
- **WHEN** `log.max_age_days` is not set in config
- **THEN** logs older than 30 days are eligible for deletion
- **AND** this applies to both `agent-*.log` daily files and `tasks/*.log` per-task files

#### Scenario: Configurable retention
- **WHEN** `log.max_age_days` is set to N (positive integer)
- **THEN** logs older than N days are eligible for deletion
- **AND** this applies to both `agent-*.log` daily files and `tasks/*.log` per-task files

#### Scenario: Retention disabled
- **WHEN** `log.max_age_days` is set to 0
- **THEN** no log file is deleted automatically (neither daily nor per-task)

#### Scenario: Periodic cleanup
- **WHEN** agent has been running
- **THEN** the logging module runs a cleanup scan every 24 hours
- **AND** deletes files matching `agent-*.log` in the log directory root whose modification time is older than the retention window
- **AND** deletes files matching `*.log` under `{log.dir}/tasks/` whose modification time is older than the retention window

#### Scenario: Cleanup runs on startup
- **WHEN** agent starts
- **THEN** logging module performs one cleanup scan (both daily and per-task files) before starting the periodic timer
