## ADDED Requirements

### Requirement: MySQL datasource configuration
The system SHALL connect to a MySQL 8 database via JDBC using `mysql-connector-j` driver. Connection parameters SHALL be configurable via environment variables with sensible defaults.

#### Scenario: Default connection parameters
- **WHEN** `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USERNAME`, `MYSQL_PASSWORD` environment variables are not set
- **THEN** the system connects to `jdbc:mysql://localhost:3306/redeploy` with default credentials

#### Scenario: Environment variable override
- **WHEN** `MYSQL_HOST=db.example.com` and `MYSQL_PORT=3307` are set
- **THEN** the system connects to `jdbc:mysql://db.example.com:3307/redeploy`

#### Scenario: MySQL unavailable at startup
- **WHEN** the MySQL server is not reachable at startup
- **THEN** the server fails to start with a clear error message
- **AND** the server does not fall back to any other database

### Requirement: MySQL schema DDL
The system SHALL define all tables using MySQL-compatible DDL syntax in `schema.sql`.

#### Scenario: Tables created with MySQL syntax
- **WHEN** the server starts against an empty MySQL database
- **THEN** all 6 tables (`groups`, `servers`, `tasks`, `deploy_history`, `artifacts`, `agents`) are created
- **AND** primary keys use `BIGINT AUTO_INCREMENT`
- **AND** boolean columns use `TINYINT(1)`
- **AND** timestamp columns use `DATETIME DEFAULT CURRENT_TIMESTAMP`

### Requirement: MySQL-compatible Mapper SQL
All MyBatis Mapper SQL SHALL use MySQL-compatible functions and syntax.

#### Scenario: Timestamp insertion
- **WHEN** an INSERT statement is executed
- **THEN** `NOW()` is used for timestamp columns instead of `datetime('now')`

#### Scenario: Auto-generated key retrieval
- **WHEN** an INSERT statement completes
- **THEN** the generated primary key is retrieved via `useGeneratedKeys` instead of `last_insert_rowid()`

### Requirement: MySQL metadata query for schema migration
The `DataMigration.ensureColumnExists` method SHALL use `INFORMATION_SCHEMA.COLUMNS` to check column existence.

#### Scenario: Column existence check
- **WHEN** `ensureColumnExists` is called for a table and column
- **THEN** it queries `INFORMATION_SCHEMA.COLUMNS` with `TABLE_SCHEMA`, `TABLE_NAME`, and `COLUMN_NAME`
- **AND** if the column does not exist, it executes `ALTER TABLE ADD COLUMN`
- **AND** if the column already exists, it skips silently

### Requirement: One-time data migration script
The system SHALL provide a shell script to migrate data from SQLite to MySQL.

#### Scenario: Execute data migration
- **WHEN** the migration script is run against a SQLite database file
- **THEN** it exports all data from 6 tables
- **AND** generates MySQL-compatible INSERT statements
- **AND** the output can be piped into `mysql` client for import

#### Scenario: Migration is stop-the-world
- **WHEN** migration is in progress
- **THEN** the server must be stopped (no dual-write support)
