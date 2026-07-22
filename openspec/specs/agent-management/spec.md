## ADDED Requirements

### Requirement: Agent auto-registration
The agent SHALL automatically register with the server on startup.

#### Scenario: Agent registers on startup
- **WHEN** agent starts and connects to server
- **THEN** agent sends registration request with hostname, ip, port, token
- **AND** server records agent as online

### Requirement: Agent heartbeat
The agent SHALL send periodic heartbeats to server.

#### Scenario: Regular heartbeat
- **WHEN** agent is running
- **THEN** agent sends heartbeat every 30 seconds to server
- **AND** server updates agent last-seen timestamp

#### Scenario: Agent marked offline
- **WHEN** server does not receive heartbeat for 90 seconds
- **THEN** server marks agent as offline

### Requirement: Agent token generation
The agent SHALL generate a unique authentication token on first startup.

#### Scenario: First startup token generation
- **WHEN** agent starts for the first time with no existing config
- **THEN** agent generates random token
- **AND** token is saved to config file
- **AND** token is printed to log file

#### Scenario: Subsequent startup
- **WHEN** agent starts with existing config
- **THEN** agent reads token from config file

### Requirement: Agent installation script
The system SHALL provide a one-click installation script for agent.

#### Scenario: Download and run install script
- **WHEN** user runs install script with server URL and token
- **THEN** script detects system architecture (amd64/arm64)
- **AND** downloads correct agent binary
- **AND** installs to /opt/deploy-agent/
- **AND** creates systemd service
- **AND** starts agent service

### Requirement: Agent binary distribution
The server SHALL provide agent binaries for download.

#### Scenario: Download agent binary
- **WHEN** GET /api/agent/download/linux/amd64 is called
- **THEN** server returns Linux amd64 agent binary

#### Scenario: Download ARM binary
- **WHEN** GET /api/agent/download/linux/arm64 is called
- **THEN** server returns Linux arm64 agent binary

### Requirement: Agent self-update
The agent SHALL support self-update mechanism.

#### Scenario: Trigger self-update
- **WHEN** POST /api/agent/update is called on agent
- **THEN** agent downloads new binary from server
- **AND** replaces current binary
- **AND** restarts itself
