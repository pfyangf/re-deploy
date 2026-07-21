## ADDED Requirements

### Requirement: Server management via REST API
The system SHALL provide REST API endpoints for managing server configurations.

#### Scenario: List all servers
- **WHEN** GET /api/servers is called
- **THEN** system returns list of all registered servers with id, name, host, port, status, group

#### Scenario: Add a new server
- **WHEN** POST /api/servers is called with name, host, port, group
- **THEN** system creates server record and returns created server with id

#### Scenario: Update server configuration
- **WHEN** PUT /api/servers/{id} is called with updated fields
- **THEN** system updates server record and returns updated server

#### Scenario: Delete a server
- **WHEN** DELETE /api/servers/{id} is called
- **THEN** system removes server record

#### Scenario: Test server connectivity
- **WHEN** POST /api/servers/{id}/test is called
- **THEN** system sends health check to agent and returns connection status

### Requirement: Web management interface
The system SHALL provide a web-based management interface accessible via browser.

#### Scenario: Access web interface
- **WHEN** user navigates to http://server-ip:9006
- **THEN** system displays management interface with server management, task management, deploy history sections

#### Scenario: Manage servers via UI
- **WHEN** user interacts with server management page
- **THEN** user can add, edit, delete, and test servers

### Requirement: Task template management
The system SHALL provide REST API and UI for managing deployment task templates.

#### Scenario: Create task template
- **WHEN** POST /api/tasks is called with task name, type, steps definition
- **THEN** system creates task template record

#### Scenario: List task templates
- **WHEN** GET /api/tasks is called
- **THEN** system returns list of all task templates

#### Scenario: Update task template
- **WHEN** PUT /api/tasks/{id} is called with updated definition
- **THEN** system updates task template

#### Scenario: Delete task template
- **WHEN** DELETE /api/tasks/{id} is called
- **THEN** system removes task template

### Requirement: Configuration persistence
The system SHALL persist all configurations in SQLite database.

#### Scenario: Database initialization
- **WHEN** server starts for the first time
- **THEN** system creates SQLite database file and initializes schema

#### Scenario: Configuration survives restart
- **WHEN** server is restarted
- **THEN** all previously configured servers, tasks, and history are preserved
