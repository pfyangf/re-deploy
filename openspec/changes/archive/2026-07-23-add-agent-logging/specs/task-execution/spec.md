## MODIFIED Requirements

### Requirement: Multi-step task execution
The agent SHALL support sequential execution of multiple steps, and SHALL log each step's start, end, complete output, and exit code to the persistent log file.

#### Scenario: Execute composite task
- **WHEN** task has multiple steps defined
- **THEN** agent executes steps in order
- **AND** stops on first failure
- **AND** returns status of each step

#### Scenario: Step execution
- **WHEN** each step is executed
- **THEN** agent records step name, start time, end time, output, exit code
- **AND** agent emits structured log records for step start and step end
- **AND** step-end log record includes complete stdout+stderr in the `output` field (not truncated)
- **AND** all step-related log records include `task_id` and `step_index`

### Requirement: Shell command execution
The agent SHALL execute shell commands as task steps, and SHALL propagate the caller's `context.Context` to enable tracing field inheritance.

#### Scenario: Execute shell command
- **WHEN** step type is "shell" with command defined
- **THEN** agent executes command via system shell
- **AND** captures stdout and stderr
- **AND** returns exit code

#### Scenario: Command timeout
- **WHEN** command execution exceeds timeout
- **THEN** agent kills the process
- **AND** returns timeout error
- **AND** emits a log record at `error` level with the timeout details and any captured partial output

#### Scenario: Execution logs carry task context
- **WHEN** a shell command is executed as part of a task step
- **THEN** log records emitted by the executor include `task_id` and `step_index` derived from the context
