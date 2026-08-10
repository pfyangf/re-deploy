## ADDED Requirements

### Requirement: Pre-deployment online check
The system SHALL check all target servers' Agent availability before starting a deployment, and SHALL abort the deployment if any server is offline.

#### Scenario: All servers online
- **WHEN** a deploy request is submitted and all target servers respond to health check within 5 seconds
- **THEN** system proceeds with the deployment normally
- **AND** updates all servers' status to "online"

#### Scenario: One or more servers offline
- **WHEN** a deploy request is submitted and one or more target servers fail health check
- **THEN** system cancels the deployment without creating a deploy history record
- **AND** returns HTTP 400 with an error message listing the offline servers
- **AND** updates the offline servers' status to "offline"

#### Scenario: Parallel health check
- **WHEN** a deploy request includes multiple servers
- **THEN** system performs health checks on all servers in parallel
- **AND** waits for all checks to complete before deciding whether to proceed

#### Scenario: Health check timeout
- **WHEN** a server does not respond to health check within 5 seconds
- **THEN** system treats the server as offline
- **AND** includes it in the offline servers list

### Requirement: AgentService connection timeout
The system SHALL configure AgentService RestTemplate with connection and read timeouts to prevent indefinite blocking.

#### Scenario: Connection timeout
- **WHEN** AgentService attempts to connect to an unreachable agent
- **THEN** the connection attempt times out after 5 seconds
- **AND** returns a connection failure result

#### Scenario: Read timeout
- **WHEN** AgentService establishes a connection but the agent does not respond within 5 seconds
- **THEN** the read times out after 5 seconds
- **AND** returns a failure result
