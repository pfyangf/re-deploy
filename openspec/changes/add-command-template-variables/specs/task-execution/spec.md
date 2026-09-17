## MODIFIED Requirements

### Requirement: Shell command execution
The agent SHALL execute shell commands as task steps, and SHALL propagate the caller's `context.Context` to enable tracing field inheritance.

When a `params` map is supplied with the task execution request, the agent SHALL perform literal `{{key}}` placeholder substitution on the command text (same rule as script steps) before invoking the system shell. Placeholders without a matching key SHALL remain unchanged. Commands executed without params SHALL behave byte-for-byte as before.

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

#### Scenario: Template variables in shell command
- **WHEN** the command contains `{{jenkinsBuildNumber}}` and params provide `jenkinsBuildNumber=123`
- **THEN** the agent substitutes the placeholder with `123` before execution

#### Scenario: Unknown placeholder stays literal
- **WHEN** the command contains `{{unknownVar}}` and no such param exists
- **THEN** the placeholder text is passed to the shell unchanged

## ADDED Requirements

### Requirement: Server SHALL inject built-in deploy template variables

When triggering a deployment, the server SHALL build a per-server copy of the params map and inject built-in variables usable in shell/command/script steps:

- `deploy.id`：本次部署记录 ID；`deploy.version`：部署版本标识（未提供时为空串）；`deploy.time`：部署开始时间 `yyyyMMdd-HHmmss`；`deploy.timestamp`：部署开始时间 epoch 秒。
- `task.id`、`task.name`：任务配置 ID 与名称。
- `server.name`、`server.host`：当前目标服务器名称与主机/IP（按每台目标分别注入）。

User-supplied params (`jenkinsBuildNumber`) and `artifactFilename` SHALL be preserved. Each target server MUST receive an independent map so parallel deployments never mutate shared state.

#### Scenario: Built-in variables reach the agent
- **WHEN** a deployment runs a pre-command `echo {{deploy.id}} {{server.host}}`
- **THEN** the executed command contains the deploy history ID and that target server's host

#### Scenario: Two servers get independent server variables
- **WHEN** one deployment targets two servers
- **THEN** each agent receives its own `server.name`/`server.host` and the maps are not shared

### Requirement: Task configuration UI SHALL list supported template variables

The task create/edit dialog SHALL display a static reference list of supported variables (name, meaning, example) near the pre-command and post-command inputs, covering Jenkins params and the deploy/task/server built-in variables.

#### Scenario: User discovers available variables
- **WHEN** a user opens the task dialog and locates the pre/post command fields
- **THEN** a variable reference list is visible without opening external documentation
