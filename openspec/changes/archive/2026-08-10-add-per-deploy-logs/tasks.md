## 1. Agent - Per-task 日志 Writer

- [x] 1.1 在 `agent/internal/logging/` 新增 `task_writer.go`：实现 `TaskWriter` 结构，负责打开/写/关闭 `{log.dir}/tasks/{taskID}.log`，内部 mutex + closed 标志，关闭后 Write 丢弃并返回 error（不 panic）
- [x] 1.2 在 `agent/internal/logging/logger.go` 新增 `OpenTaskLog(taskID string) (*TaskWriter, error)` 与 `CloseTaskLog(taskID string)`，维护 taskID -> *TaskWriter 的 map（带独立 mutex）
- [x] 1.3 新增 `TaskFanOutWriter`：包装 `DailyWriter` + 当前 task 的 `TaskWriter`，实现 `io.Writer`；`Write` 时同时写两者，task writer 写失败仅记 error 不阻断 daily
- [x] 1.4 扩展 `logging/context.go`：新增 `ctxKeyTaskLogger` 与 `WithTaskLogger(ctx, *slog.Logger)`、task logger 从 `FromContext` 优先返回（fallback 到 default）
- [x] 1.5 `logging.Init` 时确保 `{log.dir}/tasks/` 目录存在（`MkdirAll`）

验证：`go build ./internal/logging/...` 通过；单测覆盖 TaskWriter open/write/close/concurrent/close-then-write 场景

## 2. Agent - Task 执行接入 task logger

- [x] 2.1 修改 `agent/internal/api/task.go` 的 `executeTask`：task 开始时调用 `logging.OpenTaskLog(execution.ID)`，构造 task-scoped `slog.Logger`（基于 `TaskFanOutWriter` + JSONHandler），通过 `logging.WithTaskLogger` 注入 context
- [x] 2.2 `executeTask` 结束（success/failed/cancelled/return 前）调用 `logging.CloseTaskLog(execution.ID)` 确保 per-task 文件关闭
- [x] 2.3 确认 `taskExecuteHandler` 和 `executeTask` 之间 context 链路正确，task logger 在所有 step logger 中生效

验证：本地起 agent，触发一次 task 执行，检查 `{log.dir}/tasks/{taskID}.log` 存在且含 task.start/task.step.*/task.end 记录；同时 daily log 也含相同记录

## 3. Agent - Task logs 端点

- [x] 3.1 在 `agent/internal/api/task.go` 新增 `taskLogsHandler`：从 path var 取 taskId，调用 `logging.ReadTaskLog(taskID)` 读取文件内容（或 `os.ReadFile`），返回 200 + body（Content-Type `application/x-ndjson`）；文件不存在返回 404
- [x] 3.2 在 `agent/internal/api/router.go` 注册路由 `GET /api/task/{taskId}/logs`（在现有 task 路由旁）
- [x] 3.3 确认该端点走现有 `authMiddleware`（需 bearer token），不绕过鉴权

验证：curl 带 token 调 `GET /api/task/{existingId}/logs` 返回日志内容；不存在的 id 返回 404；无 token 返回 401

## 4. Agent - Cleaner 扩展

- [x] 4.1 修改 `agent/internal/logging/cleaner.go` 的 `runCleanup`：除扫描 `{log.dir}/agent-*.log` 外，再扫描 `{log.dir}/tasks/*.log`，按相同 mtime + MaxAgeDays 阈值删除
- [x] 4.2 确认 `MaxAgeDays <= 0` 时 tasks 目录也不清理（与 daily 一致）

验证：构造 mtime 老于阈值的 `tasks/xxx.log`，触发 `runCleanup`，确认被删；`MaxAgeDays=0` 时不删

## 5. Server - 三层 schema 演进机制（双保险 + 增量）

- [x] 5.1 新建 `server/src/main/resources/migration/` 目录
- [x] 5.2 新建 `server/src/main/resources/migration/V001__add_detail_logs.sql`，内容 `ALTER TABLE deploy_history ADD COLUMN detail_logs TEXT;`
- [x] 5.3 修改 `server/src/main/resources/schema.sql` 的 `deploy_history` CREATE TABLE 语句，加入 `detail_logs TEXT` 列（新库直接建全）
- [x] 5.4 修改 `server/src/main/resources/application.yml` 的 `spring.sql.init`：`mode: never` -> `always`（修正 schema.sql 长期失效）；新增 `data-locations: classpath:migration/*.sql` 与 `continue-on-error: true`
- [x] 5.5 在 `DataMigration.onApplicationReady` 加 `ensureColumnExists("deploy_history", "detail_logs", "TEXT")` 作为 Java 兜底
- [x] 5.6 验证启动：老库（无 detail_logs）启动后列存在；新库（空）启动后列存在；重复启动不报错

验证：删 db 重启 -> ①建全表，列存在；用旧 db 重启 -> ②ALTER 或 ③ensureColumnExists 补列；重复启动 -> ②报错被 continue-on-error 吞，③ PRAGMA 检查列已存在跳过

## 6. Server - 模型与 Mapper

- [x] 6.1 `DeployHistory.java` 新增 `detailLogs` 字段 + getter/setter
- [x] 6.2 `DeployHistoryMapper.java` 的 `@Insert`/`@Update` SQL 加入 `detail_logs` 列；`@Select` 查询包含 `detail_logs`（或在详情查询单独加方法）
- [x] 6.3 新增 `@Select` 方法 `findByIdWithDetail`（或复用 `findById` 直接含 detail_logs）用于详情查询

验证：mvn compile 通过；手工调 mapper 插入/查询带 detail_logs 的记录

## 7. Server - 日志拉取与聚合

- [x] 7.1 在 `DeployService` 新增 `fetchTaskLogs(Server server, String taskId)` 方法：`GET http://{host}:{port}/api/task/{taskId}/logs`，带 bearer token，返回 String（原始日志内容）；404 -> `[agent 版本过低，无日志]`；其他异常 -> `[拉取失败: <msg>]`
- [x] 7.2 修改 `pollTaskStatus`：拿到终态后调用 `fetchTaskLogs`，把返回内容塞入 `DeployResult`（新增 `taskLogs` 字段）
- [x] 7.3 修改 `DeployService.deploy` 的聚合逻辑：遍历 `DeployResult`，按 server 分段格式化为可读文本（`===== [name host] =====\n{日志}\n`），拼接成 `detailLogs`，写入 `history.setDetailLogs(...)`
- [x] 7.4 server 端解析 agent 返回的 ndjson 日志行，格式化为可读文本（task.start / task.step.start / task.step.end / task.end 的事件 + output 字段）；解析失败时 fallback 存原始文本
- [x] 7.5 确认 `logs`（summary）仍按原逻辑写入，不被 detailLogs 影响

验证：触发一次多 server 部署，完成后查 `deploy_history.detail_logs` 含分段内容；模拟 agent 404 -> 该段为占位符；模拟 agent 宕机 -> 该段为 `[拉取失败...]`

## 8. Server - 详情查询 API

- [x] 8.1 在 `DeployController` 新增 `@GetMapping("/{id}/detail")`：返回 `DeployHistory`（含 detailLogs）；不存在返回 404
- [x] 8.2 确认现有 `getDeployStatus` 与 history 列表接口不返回 detail_logs（避免列表大字段）；只在 detail 接口返回

验证：curl `GET /api/deploy/{id}/detail` 返回含 detailLogs；`GET /api/deploy/history` 返回项无 detailLogs 字段（或为 null 且体量小）

## 9. Frontend - 详情 Drawer

- [x] 9.1 在 `frontend/src/api/client.js` 新增 `getDeployDetail(id)` 调用 `GET /api/deploy/{id}/detail`
- [x] 9.2 修改 `frontend/src/views/History.vue`：`viewDetail` 改为调 `getDeployDetail`，打开 `el-drawer`（或 el-dialog）
- [x] 9.3 Drawer 上半部分展示 summary（`logs` 字段，原样或简单 `<pre>` 渲染）；下半部分按 `===== [server ...] =====` 分隔符切段，每段一个折叠面板（el-collapse）展示 detail_logs
- [x] 9.4 detail_logs 为 null 时显示"该记录为历史数据，无详细日志"
- [x] 9.5 段落标题解析 server 名/host（从 `===== [name host] =====` 提取），作为 collapse title

验证：前端构建通过（npm run build）；点击详情打开 drawer，summary + 分段 detail 正常显示；老记录显示提示

## 10. 文档与规约

- [x] 10.1 更新 `AGENTS.md` 的 Data & Schema 段：改写为三层 schema 演进机制（① schema.sql mode:always 建 CREATE TABLE IF NOT EXISTS 全量列；② migration/VNNN__name.sql 放增量 DDL，continue-on-error 容错；③ DataMigration.ensureColumnExists 兜底加列）。新增列时三层同步更新
- [x] 10.2 更新 `AGENTS.md` 的 Deploy flow gotchas 段：补 per-task 日志文件位置 `{log.dir}/tasks/{taskID}.log` 与 `GET /api/task/{id}/logs` 端点说明

## 11. 端到端验证

- [x] 11.1 全量构建：agent 交叉编译（linux/amd64 + arm64）+ server mvn package + 前端 npm run build 均通过
- [ ] 11.2 启动 server，触发一次部署到 2+ server，完成后在前端详情页验证 summary + 分段 detail 完整展示（需真实部署环境，由用户验证）
- [ ] 11.3 验证兼容：对老 agent（无 logs 端点）触发部署，detail 中该 server 段显示 `[agent 版本过低，无日志]`，部署本身不受影响（需真实环境，由用户验证）
- [ ] 11.4 验证老 deploy_history 记录详情页：detail_logs 为 null 时只显示 summary（需真实环境，由用户验证）
- [ ] 11.5 验证 cleaner：构造老 `tasks/*.log` 文件，等 cleaner 触发或手动调，确认按 MaxAgeDays 清理（需真实环境，由用户验证）
