## ADDED Requirements

### Requirement: Deploy history recording
The system SHALL record all deployment operations.

#### Scenario: Record successful deployment
- **WHEN** deployment completes successfully
- **THEN** system records task name, server, version, status, start time, end time, logs

#### Scenario: Record failed deployment
- **WHEN** deployment fails
- **THEN** system records task name, server, version, status, error message, logs

### Requirement: Deploy history query
The system SHALL provide API to query deployment history.

#### Scenario: List deployment history
- **WHEN** GET /api/deploy/history is called
- **THEN** system returns list of deployments sorted by time descending

#### Scenario: Filter by server
- **WHEN** GET /api/deploy/history?server_id={id} is called
- **THEN** system returns deployments for specified server only

#### Scenario: Filter by status
- **WHEN** GET /api/deploy/history?status=failed is called
- **THEN** system returns failed deployments only

### Requirement: Deploy status tracking
The system SHALL track real-time deployment status.

#### Scenario: Query deployment status
- **WHEN** GET /api/deploy/{task_id}/status is called
- **THEN** system returns current deployment status
- **AND** returns per-server status if multi-server deployment

#### Scenario: Deployment in progress
- **WHEN** deployment is currently executing
- **THEN** status returns "running" with progress information

### Requirement: Execution log storage
The system SHALL store execution logs for each deployment.

#### Scenario: Store agent execution logs
- **WHEN** agent completes task execution
- **THEN** logs are sent back to server
- **AND** server stores logs with deployment record

#### Scenario: Query execution logs
- **WHEN** user requests logs for specific deployment
- **THEN** system returns detailed execution logs including stdout/stderr

### Requirement: Log cleanup
The system SHALL automatically clean up old deployment history.

#### Scenario: Automatic cleanup
- **WHEN** deployment records are older than 7 days
- **THEN** system deletes old records and associated logs

#### Scenario: Manual cleanup trigger
- **WHEN** POST /api/deploy/cleanup is called
- **THEN** system immediately runs cleanup process
