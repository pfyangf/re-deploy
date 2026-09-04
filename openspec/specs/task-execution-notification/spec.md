# task-execution-notification Specification

## Purpose
TBD - created by archiving change notification-management-and-task-events. Update Purpose after archive.

## Requirements

### Requirement: System event types
The system SHALL emit seven types of system events from deploy / task / agent subsystems.

#### Scenario: Task lifecycle events
- **WHEN** a deploy task transitions to started / success / failed / cancelled
- **THEN** the system emits `task.started` / `task.success` / `task.failed` / `task.cancelled` events

#### Scenario: Step-level events
- **WHEN** an individual task step transitions to failed or times out
- **THEN** the system emits `step.failed` / `step.timeout` events

#### Scenario: Agent lifecycle events
- **WHEN** an agent transitions to online (heartbeat recovered) or offline (heartbeat missed threshold)
- **THEN** the system emits `agent.online` / `agent.offline` events

### Requirement: Event payload schema
Every emitted system event SHALL carry a consistent payload structure for downstream dispatchers.

#### Scenario: Standard event fields
- **WHEN** an event is emitted
- **THEN** it contains: `eventType`, `serverId`, `taskId`, `taskName`, `deployHistoryId`, `version`, `errorMessage`, `occurredAt`, `extras` (map of additional context)

### Requirement: Subscription matching
The notification dispatcher SHALL match events against notification configs using three filters.

#### Scenario: Event-type filter
- **WHEN** an event is emitted
- **THEN** the dispatcher only considers configs whose `event_types` JSON array contains the event's `eventType`

#### Scenario: Server-group filter
- **WHEN** a config has non-empty `server_group_ids`
- **THEN** the dispatcher only sends when the event's `serverId` belongs to one of those server groups

#### Scenario: Task-template filter
- **WHEN** a config has non-empty `task_template_ids`
- **THEN** the dispatcher only sends when the event's task originates from one of those task templates

#### Scenario: Empty filter means unrestricted
- **WHEN** `server_group_ids` or `task_template_ids` is empty/null
- **THEN** that filter is skipped (treated as "any")

### Requirement: Async dispatch
Event-to-notification dispatch SHALL be asynchronous and never block the emitting thread.

#### Scenario: Non-blocking publish
- **WHEN** `DeployService` publishes a `task.started` event
- **THEN** the publish call returns immediately
- **AND** notification delivery happens on the dispatcher's executor pool

#### Scenario: Dispatcher error isolation
- **WHEN** one channel's send fails
- **THEN** other channels' sends and other configs' sends continue unaffected

### Requirement: Event emission points
The system SHALL emit events from the following call sites.

#### Scenario: DeployService emits task events
- **WHEN** `DeployService` triggers a deploy
- **THEN** it emits `task.started` on dispatch
- **AND** `task.success` / `task.failed` on terminal state
- **AND** `task.cancelled` if the deploy is cancelled before completion

#### Scenario: Step-level events from agent callbacks
- **WHEN** the server polls an agent and receives a step that exited non-zero
- **THEN** it emits `step.failed` with the failing step's name and exit code
- **WHEN** the step's `elapsed > configured timeout`
- **THEN** it emits `step.timeout`

#### Scenario: Agent heartbeat events
- **WHEN** `AgentHeartbeatService` marks an agent as offline
- **THEN** it emits `agent.offline`
- **WHEN** the agent resumes heartbeat
- **THEN** it emits `agent.online`
