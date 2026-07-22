## ADDED Requirements

### Requirement: View task details
The system SHALL allow users to view complete details of an existing task in a read-only dialog from the task list.

#### Scenario: Open task details dialog
- **WHEN** user clicks the "View" button for a task in the task list
- **THEN** a read-only dialog opens displaying all task configuration including:
  - Task name, description, type
  - Group assignment
  - Deployment path, before/after commands
  - All Jenkins configuration (if enabled)
  - Creation and update time

### Requirement: Edit existing task
The system SHALL allow users to edit an existing task through an editable dialog from the task list.

#### Scenario: Open task for editing
- **WHEN** user clicks the "Edit" button for a task in the task list
- **THEN** an editable dialog opens with all current task values pre-filled
- **AND** user can modify any field including Jenkins configuration

#### Scenario: Save edited task
- **WHEN** user modifies fields and clicks "Save"
- **THEN** the task is updated in the database
- **AND** the task list is refreshed
- **AND** the dialog closes

#### Scenario: Cancel editing
- **WHEN** user clicks "Cancel" while editing
- **THEN** the dialog closes without saving any changes

### Requirement: Fix view button click handler
The "View" button in the task list SHALL have a working click handler that opens the task details dialog.

#### Scenario: Click view button
- **WHEN** user clicks the "View" button
- **THEN** the task details dialog opens (no JavaScript error)

### Requirement: Operation column button layout
The operation column in the task list SHALL have enough width to display all three buttons (View/Edit/Delete) on a single line without wrapping.

#### Scenario: Three buttons display correctly
- **WHEN** the task list is rendered with View, Edit, and Delete buttons for a row
- **THEN** all three buttons are displayed on a single horizontal line
- **AND** no button wrapping or line break occurs

## MODIFIED Requirements
(None - this is new capability)

## REMOVED Requirements
(None)
