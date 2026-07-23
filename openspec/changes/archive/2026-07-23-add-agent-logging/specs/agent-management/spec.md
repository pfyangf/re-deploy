## ADDED Requirements

### Requirement: Log configuration fields
The agent configuration file SHALL support a `log` section that controls log output location, level, and retention.

#### Scenario: Log config with all fields
- **WHEN** `config.yaml` contains a `log` section with `dir`, `level`, and `max_age_days`
- **THEN** agent uses those values to initialize the logger

#### Scenario: Log config with missing fields
- **WHEN** `config.yaml` omits any field within the `log` section
- **THEN** agent applies defaults: `dir=/opt/deploy-agent/log`, `level=info`, `max_age_days=30`

#### Scenario: Log config entirely absent
- **WHEN** `config.yaml` does not contain a `log` section
- **THEN** agent applies all default values
- **AND** agent starts successfully without prompting or error

### Requirement: Agent installation prepares log directory
The agent installation script SHALL create the log directory during installation so that the agent can write logs immediately on first startup.

#### Scenario: Install script creates log directory
- **WHEN** the install script runs on a target server
- **THEN** the script creates `/opt/deploy-agent/log/` with appropriate permissions for the agent user
