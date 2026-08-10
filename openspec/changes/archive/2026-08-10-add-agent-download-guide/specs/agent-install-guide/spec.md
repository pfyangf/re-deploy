## ADDED Requirements

### Requirement: Agent install guide dialog
The system SHALL provide an Agent installation guide dialog accessible from the server management page toolbar.

#### Scenario: Open install guide from toolbar
- **WHEN** user clicks "下载 Agent" button on the server management page toolbar
- **THEN** system opens a dialog titled "Agent 安装指南"

#### Scenario: Download command with architecture selection
- **WHEN** user selects Linux amd64 or Linux arm64 in the install guide
- **THEN** the download command in step 1 updates to reflect the selected architecture
- **AND** the server URL in the command is automatically filled with the current page origin

#### Scenario: Systemd service configuration
- **WHEN** user views step 2 of the install guide
- **THEN** system displays the complete systemd service unit file content
- **AND** provides a copy button to copy the entire setup command

#### Scenario: Token retrieval instruction
- **WHEN** user views step 3 of the install guide
- **THEN** system displays the command to view the Agent token from config.yaml
- **AND** provides a copy button for the command

#### Scenario: Common commands reference
- **WHEN** user views the install guide
- **THEN** system displays a list of common commands including status check, log viewing, restart, stop, and health check

#### Scenario: Directory structure display
- **WHEN** user views the install guide
- **THEN** system displays the Agent directory structure with descriptions for each directory

#### Scenario: Quick add server shortcut
- **WHEN** user clicks "去添加服务器" button at the bottom of the install guide
- **THEN** the install guide dialog closes
- **AND** the add server dialog opens

#### Scenario: Close install guide
- **WHEN** user clicks close button or "关闭" button
- **THEN** the install guide dialog closes
