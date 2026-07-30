## ADDED Requirements

### Requirement: Jenkins build history API
The system SHALL provide an API endpoint to fetch the build history for a Jenkins-enabled task, acting as a proxy to the Jenkins server.

#### Scenario: Fetch build history for Jenkins task
- **WHEN** a client requests build history for a task that has Jenkins enabled
- **THEN** the server queries the Jenkins API for the configured job
- **AND** returns a list of builds with build number, result, timestamp, and description
- **AND** builds are sorted by build number descending (newest first)
- **AND** at most 20 builds are returned by default

#### Scenario: Build history for non-Jenkins task
- **WHEN** a client requests build history for a task that does not have Jenkins enabled
- **THEN** the API returns an error indicating Jenkins is not configured for this task

#### Scenario: Jenkins unreachable
- **WHEN** the server cannot reach the Jenkins server or authentication fails
- **THEN** the API returns an error with a descriptive message

### Requirement: Build history selection on deploy page
The deploy page SHALL allow users to select a Jenkins build number from a build history list instead of manually typing it.

#### Scenario: Fetch build history button
- **WHEN** the user selects a Jenkins-enabled task on the deploy page
- **THEN** a "Fetch" button appears next to the build number input field
- **AND** clicking the button triggers a request to fetch the build history

#### Scenario: Select build from history
- **WHEN** the build history list is displayed
- **AND** the user clicks on a build entry
- **THEN** the build number is automatically filled into the build number input field
- **AND** the history list is closed

#### Scenario: Manual input still works
- **WHEN** the build number input field is displayed
- **THEN** the user can still manually type any build number
- **AND** manual input is accepted for deployment regardless of whether the history was fetched
