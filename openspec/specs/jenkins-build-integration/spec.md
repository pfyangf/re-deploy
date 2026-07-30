### Requirement: Task level Jenkins configuration
The system SHALL allow users to configure Jenkins integration when creating or editing a task. Configuration SHALL include the following fields:
- Enable/disable toggle for Jenkins build download
- Jenkins base URL
- Jenkins Job full name (including any path segments)
- Artifact path relative to the build
- Jenkins username for authentication
- Jenkins API token for authentication

#### Scenario: Configure Jenkins for new task
- **WHEN** user creates a new deploy task and checks "Enable Jenkins"
- **AND** user fills in Jenkins URL, Job name, artifact path, username, and token
- **AND** user submits the form
- **THEN** the task is saved with all Jenkins configuration fields stored in the database

#### Scenario: Edit Jenkins configuration for existing task
- **WHEN** user edits an existing task with Jenkins enabled
- **AND** user modifies the artifact path
- **AND** user saves
- **THEN** the updated Jenkins configuration is persisted

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

### Requirement: Dynamic artifact download from Jenkins during deployment
When a deployment is triggered for a Jenkins-enabled task, the system SHALL download the artifact from Jenkins using the configured URL, Job name, artifact path, and the provided build number before proceeding with deployment to target servers.

#### Scenario: Successful artifact download
- **WHEN** deployment is triggered for a Jenkins-enabled task with a valid build number
- **AND** Jenkins is reachable and returns the artifact successfully
- **THEN** the artifact is downloaded to the server's local storage
- **AND** the artifact is uploaded to each target agent via the existing file transfer mechanism
- **AND** deployment continues normally

#### Scenario: Missing build number
- **WHEN** deployment is triggered for a Jenkins-enabled task without providing a build number
- **THEN** the deployment fails immediately
- **AND** an error message is displayed indicating that build number is required

#### Scenario: Jenkins download fails
- **WHEN** deployment is triggered and Jenkins download fails due to network error or authentication error
- **THEN** the deployment fails
- **AND** the error is logged and displayed to the user

### Requirement: Artifact cache cleanup
The system SHALL automatically clean up old Jenkins artifacts to prevent disk space exhaustion. For each Jenkins Job, ONLY the 3 most recently downloaded builds SHALL be retained. Older builds SHALL be deleted when a new build is downloaded.

#### Scenario: Cleanup old builds
- **WHEN** a new build is downloaded for a Jenkins Job that already has 3 downloaded builds
- **THEN** the oldest downloaded build (by modification time) is deleted from disk before the new build is saved

### Requirement: Deploy step implementation
The agent SHALL implement a `deploy` step type that copies the uploaded artifact to the configured target deployment path.

#### Scenario: Copy artifact to deployment path
- **WHEN** the agent executes a `deploy` step
- **AND** the step specifies a target deployment path
- **AND** the artifact has been successfully uploaded
- **THEN** the artifact file is copied from the agent's artifact directory to the target deployment path
