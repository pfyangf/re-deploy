## ADDED Requirements

### Requirement: Detailed deployment log storage
The system SHALL store a detailed deployment log for each deployment record, containing per-server, per-step execution output aggregated from all participating agents.

#### Scenario: Store detailed logs after deployment
- **WHEN** a deployment completes (success, failure, or partial failure)
- **THEN** the system stores aggregated detailed logs in `deploy_history.detail_logs`
- **AND** the detailed logs are segmented by server with clear delimiters
- **AND** each segment includes the task lifecycle events and per-step output

#### Scenario: Partial logs on agent retrieval failure
- **WHEN** one or more agents fail to return their task logs (e.g., agent offline, version too old, timeout)
- **THEN** the system stores logs for all successfully-retrieved servers
- **AND** for each failed server, a placeholder segment `[拉取失败: <reason>]` is inserted
- **AND** the deployment status is not affected by log retrieval failures

#### Scenario: Legacy agent without logs endpoint
- **WHEN** an agent returns 404 for `GET /api/task/{id}/logs` (agent version predates this feature)
- **THEN** that server's segment is `[agent 版本过低，无日志]`
- **AND** other servers' logs are still retrieved and stored normally

### Requirement: Deployment detail query API
The system SHALL provide an API to query a deployment's detail logs.

#### Scenario: Query deployment detail
- **WHEN** `GET /api/deploy/{id}/detail` is called
- **THEN** the system returns the deploy history record including `detail_logs`
- **AND** if `detail_logs` is null (legacy record), the field is null in the response

#### Scenario: Deployment not found
- **WHEN** `GET /api/deploy/{id}/detail` is called with a non-existent id
- **THEN** the system returns HTTP 404

### Requirement: Three-layer schema evolution
The system SHALL evolve database schema via a three-layer mechanism: (1) `schema.sql` with `CREATE TABLE IF NOT EXISTS` defining full columns, executed on every startup; (2) versioned migration SQL files under `migration/` for incremental DDL (ADD COLUMN, CREATE INDEX, constraints, etc.), tolerating repeated execution via `continue-on-error`; (3) `DataMigration.ensureColumnExists` Java fallback for ADD COLUMN only. All three layers SHALL be kept in sync when adding new columns.

#### Scenario: schema.sql executed on every startup
- **WHEN** the server starts
- **THEN** `schema.sql` is executed (mode: always)
- **AND** new databases get all tables with full columns defined in CREATE TABLE
- **AND** existing databases skip existing tables via IF NOT EXISTS

#### Scenario: Migration SQL file applied for incremental DDL
- **WHEN** a migration file `V001__add_detail_logs.sql` containing `ALTER TABLE deploy_history ADD COLUMN detail_logs TEXT` exists
- **THEN** on server startup, the migration is applied
- **AND** the `detail_logs` column exists on `deploy_history` table

#### Scenario: Migration is idempotent on re-run
- **WHEN** the server restarts after the migration has already been applied
- **THEN** the repeated ALTER is tolerated (error swallowed via continue-on-error) and startup continues
- **AND** existing data in `detail_logs` is preserved

#### Scenario: New database gets full schema, migration is no-op
- **WHEN** a new database is created
- **THEN** `schema.sql` creates `deploy_history` with the `detail_logs` column included in the CREATE TABLE statement
- **AND** the migration ALTER reports "column already exists" error which is swallowed

#### Scenario: Java fallback ensures column exists
- **WHEN** the ApplicationReadyEvent fires after schema.sql and migration SQL have run
- **THEN** `DataMigration.ensureColumnExists` checks via PRAGMA table_info
- **AND** if the column still does not exist (edge case), it executes ALTER TABLE ADD COLUMN
- **AND** if the column already exists, it skips silently

## MODIFIED Requirements

### Requirement: Execution log storage
The system SHALL store execution logs for each deployment at two granularities: a summary in `logs` for list preview, and detailed per-server per-step logs in `detail_logs` for detail view.

#### Scenario: Store agent execution logs
- **WHEN** agent completes task execution
- **THEN** the server retrieves the task's per-task log file from the agent via `GET /api/task/{id}/logs`
- **AND** the server aggregates logs from all participating servers (segmented by server) into `deploy_history.detail_logs`
- **AND** the server retains the existing summary in `deploy_history.logs`

#### Scenario: Query execution logs
- **WHEN** user requests logs for a specific deployment via the detail view
- **THEN** the system returns the summary (`logs`) and the detailed logs (`detail_logs`) separately
- **AND** the detailed logs are segmented by server for rendering

#### Scenario: Legacy records without detail logs
- **WHEN** a deployment record was created before this feature (detail_logs is null)
- **THEN** the detail view shows only the summary
- **AND** the detail view indicates detailed logs are unavailable for legacy records
