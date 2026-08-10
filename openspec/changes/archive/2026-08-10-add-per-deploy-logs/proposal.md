## Why

当前部署历史只存一行 summary（`Server: a, Success: true...`），详情按钮是 stub。Agent 端其实已经返回完整步骤数据，但 server 把它丢了；agent 日志按天混写，无法按单次部署回溯。需要让每次部署的完整日志可查、可追溯。

## What Changes

- Agent 新增 per-task 日志文件：task 执行期间同时写 `agent-YYYY-MM-DD.log`（daily）和 `tasks/{taskID}.log`（per-task），task 结束后关闭 per-task 文件。
- Agent 新增 `GET /api/task/{taskId}/logs` 端点：返回 `{taskID}.log` 文件内容。
- Agent 的 cleaner 扫描规则扩展：除 `agent-*.log` 外，同时清理 `tasks/*.log`（复用 `log.max_age_days`）。
- Server 的 `pollTaskStatus` 在拿到终态后，再调 agent `/api/task/{id}/logs` 拉取该 task 的完整日志，按 server 分段聚合后存入 `deploy_history.detail_logs`（新增列）。
- 某个 server 的 agent 日志拉取失败时，该 server 段写 `[拉取失败: <原因>]`，其余 server 正常入库（partial 日志）。
- 保留现有 `deploy_history.logs`（summary），列表预览用；详情页先展示 summary，再展开 `detail_logs`（按 server 分段）。
- Server DDL 规约调整：建立三层 schema 演进机制（① `schema.sql` mode:always 建 CREATE TABLE IF NOT EXISTS 全量列；② `migration/VNNN__name.sql` 放增量 DDL，continue-on-error 容错；③ `DataMigration.ensureColumnExists` 兜底加列），顺手修正 `spring.sql.init.mode: never` 长期失效问题。
- 前端 `History.vue` 详情按钮打开 drawer，上半展示 summary，下半按 server 分段展示完整日志。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `agent-logging`: 新增 per-task 日志文件归档能力 + cleaner 扫描 `tasks/*.log` + `GET /api/task/{id}/logs` 端点
- `deploy-history`: 新增 `detail_logs` 字段存储完整部署日志；新增按 server 分段聚合的日志拉取与存储；新增详情查询 API；新增三层 schema 演进机制（修正 mode:never 失效 + migration SQL + Java 兜底）

## Impact

- **Agent (Go)**: `internal/logging/`（新增 TaskWriter + fan-out）、`internal/api/task.go`（task logger 注入）、`internal/api/router.go`（新端点）、`internal/logging/cleaner.go`（扫描规则）
- **Server (Java)**: `DeployService.pollTaskStatus`（拉取并聚合日志）、`DeployHistory` 模型 + Mapper + DDL、`DeployController`（详情 API）、`schema.sql` + 新增 migration SQL 文件 + `application.yml` mode 修正 + `DataMigration` 兜底
- **Frontend (Vue)**: `History.vue` 详情 drawer
- **DB**: `deploy_history` 表新增 `detail_logs TEXT` 列（三层机制保障）
- **兼容性**: 旧 deploy_history 记录 `detail_logs` 为 null，详情页只显示 summary；agent 老版本无 `/api/task/{id}/logs` 端点时，server 标记该 server 段为 `[agent 版本过低，无日志]`
- **副作用**: 修正 `spring.sql.init.mode: never` -> `always`，schema.sql 将真正每次启动执行（此前长期失效）；对已有 IF NOT EXISTS 语句无破坏性影响
