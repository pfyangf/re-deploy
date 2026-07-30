## ADDED Requirements

### Requirement: Pipeline 领域模型

系统 SHALL 引入 Pipeline 作为部署流程的顶级抽象，一个 Pipeline 包含多个 Stage，一个 Stage 包含多个 Step。

#### Scenario: Pipeline 实体
- **THEN** `Pipeline` MUST 包含以下字段：
  - `id`：主键
  - `name`：流水线名称
  - `description`：描述
  - `groupId`：所属分组
  - `sourceTaskId`：原 Task ID（兼容字段，可为空）
  - `createdAt`、`updatedAt`

#### Scenario: Stage 实体
- **THEN** `PipelineStage` MUST 包含以下字段：
  - `id`：主键
  - `pipelineId`：所属流水线
  - `name`：阶段名称
  - `sequence`：执行顺序
  - `executionMode`：`sequential` 或 `parallel`
  - `condition`：执行条件表达式（可选）

#### Scenario: Step 实体
- **THEN** `PipelineStep` MUST 包含以下字段：
  - `id`：主键
  - `stageId`：所属阶段
  - `name`：步骤名称
  - `sequence`：执行顺序
  - `type`：步骤类型
  - `config`：JSON 格式的步骤配置
  - `timeoutSeconds`：超时时间
  - `retryCount`：失败重试次数
  - `ignoreFailure`：是否忽略失败继续执行

### Requirement: Run 执行实例模型

系统 SHALL 引入 Run 作为 Pipeline 的一次执行实例，记录 Stage/Step 级执行状态。

#### Scenario: Run 实体
- **THEN** `Run` MUST 包含以下字段：
  - `id`：主键
  - `pipelineId`：所属流水线
  - `version`：部署版本号
  - `serverIds`：目标服务器 ID 列表（JSON 数组）
  - `status`：运行状态
  - `startedAt`、`completedAt`
  - `createdBy`：触发人
  - `logs`：汇总日志
  - `errorMessage`：错误信息

#### Scenario: RunStage 实体
- **THEN** `RunStage` MUST 包含以下字段：
  - `id`：主键
  - `runId`：所属 Run
  - `pipelineStageId`：对应 PipelineStage
  - `name`：阶段名称
  - `status`：状态
  - `startedAt`、`completedAt`

#### Scenario: RunStep 实体
- **THEN** `RunStep` MUST 包含以下字段：
  - `id`：主键
  - `runStageId`：所属 RunStage
  - `pipelineStepId`：对应 PipelineStep
  - `serverId`：目标服务器（并行到多机时）
  - `name`：步骤名称
  - `type`：步骤类型
  - `status`：状态
  - `output`：标准输出
  - `errorOutput`：错误输出
  - `startedAt`、`completedAt`

### Requirement: 步骤类型

系统 SHALL 在一期支持 4 种 Step 类型：`shell`、`copy`、`healthcheck`、`approve`。

#### Scenario: shell 步骤
- **WHEN** Step 类型为 `shell`
- **THEN** `config` MUST 包含 `command` 字段
- **AND** Agent MUST 使用 `sh -c` 执行该命令

#### Scenario: copy 步骤
- **WHEN** Step 类型为 `copy`
- **THEN** `config` MUST 包含 `source` 和 `destination` 字段
- **AND** Server MUST 将 source 文件上传到 Agent 的 destination 路径

#### Scenario: healthcheck 步骤
- **WHEN** Step 类型为 `healthcheck`
- **THEN** `config` MUST 包含 `url` 字段，可选 `method`、`expectedStatus`、`timeoutSeconds`
- **AND** Agent 或 Server MUST 发起 HTTP 探测并校验状态码

#### Scenario: approve 步骤
- **WHEN** Step 类型为 `approve`
- **THEN** `config` MUST 包含 `message` 字段
- **AND** Run MUST 暂停在该步骤，等待 UI 调用审批接口确认或拒绝

### Requirement: Run 状态机

系统 SHALL 为 Run / RunStage / RunStep 实现显式状态机。

#### Scenario: 状态定义
- **THEN** 状态 MUST 包含：`pending`、`queued`、`running`、`success`、`failed`、`cancelled`

#### Scenario: 合法状态转换
- **WHEN** Run 处于 `pending`
- **THEN** 只能转换到 `queued` 或 `cancelled`
- **WHEN** Run 处于 `running`
- **THEN** 只能转换到 `success`、`failed` 或 `cancelled`

#### Scenario: 状态转换方法
- **THEN** 状态转换 MUST 通过 `PipelineRunService` 的显式方法执行，如 `startRun(runId)`、`markStepSuccess(stepId)`、`markStepFailed(stepId, error)`、`cancelRun(runId)`
- **AND** 业务代码 MUST NOT 直接调用 `setStatus()`

### Requirement: Stage 执行模式

系统 SHALL 支持 Stage 内的串行与并行执行。

#### Scenario: 串行 Stage
- **WHEN** `executionMode = sequential`
- **THEN** Stage 内 Step MUST 按 `sequence` 顺序依次执行
- **AND** 前一步失败且未配置 `ignoreFailure` 时 MUST 停止该 Stage

#### Scenario: 并行 Stage
- **WHEN** `executionMode = parallel`
- **THEN** Stage 内 Step MUST 同时启动
- **AND** 所有 Step 完成后，任一 Step 失败且未忽略失败时该 Stage 标记为 failed

### Requirement: 步骤级超时与重试

系统 SHALL 支持每个 Step 独立配置超时与重试。

#### Scenario: 超时
- **WHEN** Step 执行时间超过 `timeoutSeconds`
- **THEN** 该 Step MUST 被标记为 `failed`
- **AND** 输出中 MUST 包含超时提示

#### Scenario: 重试
- **WHEN** Step 失败且 `retryCount > 0`
- **THEN** 系统 MUST 按重试次数重新执行该 Step
- **AND** 最终仍失败时才标记为 `failed`

### Requirement: 条件执行

系统 SHALL 支持 Step 和 Stage 根据条件表达式决定是否执行。

#### Scenario: 条件表达式
- **WHEN** `condition` 字段非空
- **THEN** 系统 MUST 在执行前解析该表达式
- **AND** 表达式为 false 时跳过该 Step/Stage，状态标记为 `skipped`

#### Scenario: 支持的变量
- **THEN** 条件表达式 MUST 可引用运行时参数，如 `{{env}} == 'production'`

## MODIFIED Requirements

- 现有 `Task` 概念被 `Pipeline` 取代，但旧 API 保留兼容层。
- 现有 `DeployHistory` 概念被 `Run` 取代，但旧 API 保留兼容层。

## REMOVED Requirements

无。
