## Why

任务配置的前置/后置命令在保存时转成 `shell` 步骤（`TaskController`），但 agent 只在 `script` 步骤做 `{{param}}` 替换，shell 步骤原样执行——用户在前置/后置命令里无法拿到 Jenkins 构建号、本次部署记录号、目标服务器 IP 等上下文（如无法在后置命令里写 RELEASE 版本文件、按构建号建备份目录）。需要让 shell/command 步骤与 script 步骤支持同一套模板变量，并提供稳定的内置变量；同时在任务配置窗口展示可用变量列表。

## What Changes

- agent 的 `shell`/`command` 步骤执行前按 params 做 `{{key}}` 字面量替换（与 `script` 步骤同一套规则：未提供的变量保持原样）；`ExecuteShell` 增加 params 参数。
- server 部署时为每台目标服务器注入内置变量（每台独立 map，顺带消除多服务器并行共享同一 params HashMap 的隐患）：
  - `jenkinsBuildNumber`（部署时用户填写，既有）、`artifactFilename`（Jenkins 制品上传后注入，既有）
  - `deploy.id`（部署记录 ID）、`deploy.version`（版本标识，未填时为空）、`deploy.time`（yyyyMMdd-HHmmss）、`deploy.timestamp`（epoch 秒）
  - `task.id`、`task.name`
  - `server.name`、`server.host`
- 前端 `TaskDialog` 前置/后置命令输入区下方新增静态「支持的变量」说明列表（变量名 + 含义 + 示例，纯展示）。

### Non-goals

- 不做条件/循环/默认值等模板表达式，只做现有 `{{key}}` 字面量替换。
- UI 调试执行（AgentService.executeCommand）无部署上下文，不注入内置变量。
- 不对变量值做 shell 转义（命令本身即用户配置，与 script 步骤威胁模型一致；文档提示值会被 shell 解释）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `task-execution`：shell/command 步骤支持 params 模板替换；部署请求携带内置模板变量。

## Impact

- **agent**：`internal/executor/executor.go`（ExecuteShell 签名）、`internal/api/task.go`（传入 req.Params）；新增 executor 单测。旧 server 不发 params 时行为不变。
- **server**：`DeployService.deployToServer` 复制并注入变量；新增可单测的变量构建方法。
- **前端**：仅 `TaskDialog.vue` 展示性改动。
- **兼容性**：纯增量；不含 `{{` 的命令字节级不变。
