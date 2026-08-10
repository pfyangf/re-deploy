## MODIFIED Requirements

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
- **THEN** `DataMigration.ensureColumnExists` checks via `INFORMATION_SCHEMA.COLUMNS`
- **AND** if the column still does not exist (edge case), it executes ALTER TABLE ADD COLUMN
- **AND** if the column already exists, it skips silently
