## MODIFIED Requirements

### Requirement: Structured logger initialization
The agent SHALL initialize a structured JSON logger at startup using Go standard library `log/slog`, without introducing third-party logging dependencies.

#### Scenario: Logger initialized before serving requests
- **WHEN** agent process starts
- **THEN** agent initializes the logger before starting the HTTP server
- **AND** logger writes JSON-formatted records
- **AND** logger uses only Go 1.21 standard library (`log/slog`)

#### Scenario: Default log location
- **WHEN** agent starts without the `-c` flag and `log.dir` is not set in config
- **THEN** logger writes to `/opt/deploy-agent/log/`

#### Scenario: Default log location in portable mode
- **WHEN** agent starts with `-c <path>` and the loaded config does not set `log.dir`
- **THEN** logger writes to a `log/` directory next to the selected config file (`<config-file-dir>/log`)
- **AND** logger never attempts to create `/opt/deploy-agent/log`

#### Scenario: Configurable log directory
- **WHEN** `log.dir` is set in config
- **THEN** logger writes to the configured directory
- **AND** creates the directory if it does not exist

#### Scenario: Default log level
- **WHEN** `log.level` is not set in config
- **THEN** logger uses `info` level
