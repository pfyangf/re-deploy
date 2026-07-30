## ADDED Requirements

### Requirement: Group selector on deploy page
The deploy page SHALL display a group selector at the top that allows users to filter tasks and servers by group. The selector SHALL include an "All groups" option.

#### Scenario: Default view shows all groups
- **WHEN** the user navigates to the deploy page
- **THEN** the group selector shows "All groups"
- **AND** the task list shows all tasks
- **AND** the server list shows all servers grouped by group name

#### Scenario: Select a specific group
- **WHEN** the user selects a specific group from the group selector
- **THEN** the task list is filtered to show only tasks belonging to that group
- **AND** the server list is filtered to show only servers belonging to that group

### Requirement: Server list display mode
When a single group is selected, the server list SHALL display servers as a flat list without group collapsing. When "All groups" is selected, servers SHALL remain grouped and collapsible as before.

#### Scenario: Flat list for single group
- **WHEN** the user selects a single group
- **THEN** the server list displays servers as a flat checkbox list
- **AND** no group headers or collapse controls are shown

#### Scenario: Grouped list for all groups
- **WHEN** the user selects "All groups"
- **THEN** the server list displays servers grouped by group name with collapsible sections

### Requirement: Clear selection on group change
When the user changes the selected group, any previously selected task and selected servers SHALL be cleared.

#### Scenario: Changing group clears selections
- **WHEN** the user has selected a task and/or some servers
- **AND** the user changes the group selector value
- **THEN** the selected task is cleared
- **AND** all selected servers are cleared
