## ADDED Requirements

### Requirement: DingTalk webhook notification
The system SHALL send DingTalk notifications on deployment failure.

#### Scenario: Send failure notification
- **WHEN** deployment fails
- **THEN** system sends DingTalk webhook message
- **AND** message includes task name, server, version, error details, timestamp

#### Scenario: Notification configuration
- **WHEN** DingTalk webhook URL is configured in application.yml
- **THEN** system uses configured URL for notifications

### Requirement: Notification content
The notification message SHALL include relevant deployment information.

#### Scenario: Failure notification content
- **WHEN** sending failure notification
- **THEN** message contains:
  - Deployment task name
  - Target server(s)
  - Application version
  - Failure reason/error
  - Timestamp
  - Link to deployment history (if applicable)

### Requirement: Notification trigger conditions
The system SHALL support configurable notification triggers.

#### Scenario: Notify on failure only
- **WHEN** notification mode is set to "failure-only"
- **THEN** system only sends notifications for failed deployments

#### Scenario: Notify on all deployments
- **WHEN** notification mode is set to "all"
- **THEN** system sends notifications for both success and failure

### Requirement: Notification retry
The system SHALL retry failed notification attempts.

#### Scenario: Notification send failure
- **WHEN** DingTalk webhook call fails
- **THEN** system retries up to 3 times with exponential backoff

### Requirement: Notification logging
The system SHALL log all notification attempts.

#### Scenario: Log notification result
- **WHEN** notification is sent (success or failure)
- **THEN** system logs notification attempt with status and response
