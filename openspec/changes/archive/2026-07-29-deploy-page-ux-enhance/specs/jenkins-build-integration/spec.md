## MODIFIED Requirements

### Requirement: Build number input at deployment
The system SHALL prompt the user for a Jenkins build number when starting a deployment for a task that has Jenkins integration enabled. The input SHALL NOT be displayed for tasks without Jenkins integration. The user SHALL be able to either manually type a build number or select one from the build history list.

#### Scenario: Display build number input for Jenkins enabled task
- **WHEN** user selects a task that has Jenkins integration enabled on the deployment page
- **THEN** a "Build Number" input field is displayed
- **AND** a "Fetch history" button is displayed next to the input
- **AND** user must enter or select a build number to start deployment

#### Scenario: Hide build number input for non-Jenkins task
- **WHEN** user selects a task that does not have Jenkins integration enabled
- **THEN** the "Build Number" input field is not displayed

#### Scenario: Select build number from history
- **WHEN** user clicks the "Fetch history" button
- **AND** the build history list loads successfully
- **AND** user clicks on a build entry
- **THEN** the build number is filled into the input field
- **AND** the user can proceed with deployment
