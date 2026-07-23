## Why

Agent 目前没有健全的日志体系：`log.Printf` 输出直接进 stdout / journalctl，运行时的关键动作（脚本执行、每步 output、退出码）根本不落盘。`executor.ExecuteShell` 把 stdout+stderr 拼成字符串返回后就丢弃，一旦 server 侧没保留响应，排查现场就永久蒸发。同时 `cleaner.go` 已经在清 `data/logs/`，但那个目录从来没人写入，是幽灵路径。

需要给 agent 建一套可持久化、可按 `task_id` 追踪、有轮转的日志系统，让部署脚本执行过程可回溯。

## What Changes

- 引入基于 Go 1.21 标准库 `log/slog` 的结构化日志，输出 JSON 一行一条
- 日志默认落到 `/opt/deploy-agent/log/agent-YYYY-MM-DD.log`，按天分割文件
- 自研 40 行 `DailyWriter`（`io.Writer` 包装 + 日期跨天检测），不引入第三方轮转库
- 日志保留天数可配置，默认 30 天；由 agent 内的清理任务负责删除过期文件
- 所有部署脚本 / 命令的完整 output **完整写入**日志（不截断），带 `task_id` / `step_index` / `exit_code` / `duration_ms` 字段
- Upload 侧日志带 `upload_id`；task 侧日志带 `task_id`；两者按现状**不打通**（server 未下发关联 ID）
- Journalctl 只保留生命周期事件（启动、停止、fatal / panic），运行时日志一律走文件
- **BREAKING（内部）**：`executor.ExecuteShell` / `ExecuteScript` 签名增加 `context.Context` 首参，用于携带 `task_id` 等追踪字段
- `config.yaml` 新增 `log` 段（`dir` / `level` / `max_age_days`），未配置时使用默认值
- `cleaner.go` 停止扫描 `data/logs/`（幽灵目录），日志清理由 logging 模块自行管理
- Agent 安装脚本（`AgentDownloadController.getInstallScript()`）创建日志目录并调整 systemd unit 的 stdout 级别（可选）

## Capabilities

### New Capabilities
- `agent-logging`: Agent 侧的结构化日志与文件轮转能力，覆盖 logger 初始化、按天切分、追踪字段传播、过期清理

### Modified Capabilities
- `task-execution`: 任务执行流程新增"每一步开始 / 结束 / 完整 output 落盘"的可观测性要求
- `agent-management`: Agent 配置新增日志相关字段（目录、级别、保留天数）

## Impact

- **Agent 代码**：
  - 新增 `agent/internal/logging/`（logger 初始化、context 传播、DailyWriter、清理协程）
  - 修改 `cmd/agent/main.go`（初始化 logger、替换 `log.Printf`）
  - 修改 `internal/config/config.go`（新增 `log` 配置段与默认值）
  - 修改 `internal/executor/executor.go`（签名加 ctx，改用 logger）
  - 修改 `internal/api/task.go`（每步事件日志、ctx 绑定 task_id）
  - 修改 `internal/api/router.go`、`upload.go`、`register.go`（替换 `log.Printf`）
  - 修改 `internal/cleaner/cleaner.go`（移除 `data/logs/` 清理项）
- **Server 代码**：`AgentDownloadController.getInstallScript()` 创建 `/opt/deploy-agent/log/` 目录、给写权限
- **配置文件**：`config.yaml` 新增 `log` 段（向后兼容，缺省即默认值）
- **依赖**：不引入新的第三方库（仅使用 Go 1.21 标准库）
- **磁盘占用**：单机每日日志量取决于部署频率与脚本 output，30 天保留策略提供上限
- **无 API 变更**：HTTP 接口契约不变
