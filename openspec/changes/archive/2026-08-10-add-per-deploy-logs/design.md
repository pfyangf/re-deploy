## Context

当前部署历史的日志能力存在三层缺口：

1. **Agent 端**：日志按天写 `agent-YYYY-MM-DD.log`，所有 task 物理上混在同一文件，只能靠 `task_id` 字段区分。`taskStatusHandler` 返回的 `TaskExecution` 结构其实包含每步的 `Output`/`ExitCode`/起止时间，但 `TaskExecution` 留在内存 map 里无清理，且没有专门的"拉日志"端点。
2. **Server 端**：`DeployService.pollTaskStatus` 只取 `status` 和 `error`，丢弃 `steps` 详情。`deploy_history.logs` 只存一行 summary（`Server: a, Success: true, Message: ...`）。
3. **前端**：`History.vue` 的 `viewDetail` 是 stub，只弹"功能开发中"。

一次 deploy_history 对应 N 个 server，每个 server 一个 agent task（uuid）。Agent 只感知 task 粒度，server 负责聚合。

DDL 现状：`application.yml` 的 `spring.sql.init.mode: never`（与 AGENTS.md 描述的 `always` 不符，schema.sql 从未被 Spring 执行）。实际建表/加列完全靠 `DataMigration.java` 的 `ensureColumnExists`（PRAGMA 检查 + ALTER）。本 change 顺手修正此失效并建立三层 schema 演进机制（见 D4）。

约束：
- Agent 用 Go 1.21 标准库 `log/slog`，不引入第三方日志库（已有 spec 约束）。
- Agent 跑在目标 Linux 服务器，文件系统可写（`/opt/deploy-agent/log/`）。
- Server 调 agent 走 HTTP + Bearer token（agent 不主动回调 server）。
- 多 server 并行部署，线程池 10。

## Goals / Non-Goals

**Goals:**
- 每次部署的完整日志（按 server 分段、含每步 stdout/stderr）可在 server 详情页查看。
- Agent 端 per-task 日志文件独立留存，可独立清理。
- 保留现有 summary `logs` 字段，列表预览不变。
- 引入增量 SQL migration 机制，不破坏 `schema.sql` 的 IF NOT EXISTS 规约。
- 兼容老 agent（无日志端点）与老 deploy_history 记录（detail_logs 为 null）。

**Non-Goals:**
- 不做日志流式推送（实时 tail）。本次只做部署完成后回查。
- 不做日志检索/全文搜索。
- 不改变 agent 主动推送模型（agent 仍不主动回调 server）。
- 不替换 `TaskExecution` 内存 map（短期保留用于 status 查询；per-task 文件是日志的真源）。
- 不动 `schema.sql` 既有 CREATE TABLE 语句。

## Decisions

### D1: Agent per-task 日志用 fan-out handler，而非替换 daily writer

**选择**：在 `logging` 包加一个 `TaskWriter`（写 `tasks/{taskID}.log`），在 `Init` 时用 `io.MultiWriter` 把 daily writer 和 task writer 组合，挂到 `slog.NewJSONHandler`。task 开始时注册 task writer，task 结束时关闭。

**备选**：用自定义 handler 在 `Handle` 里判断 `task_id` 属性再分流。被否：handler 要维护 taskID -> file 的 map 并加锁，复杂度高；fan-out 在 writer 层做更简单，且 daily 文件保持完整。

**备选**：只在 task 结束时从 `TaskExecution` 结构 dump 到文件。被否：task 中途崩溃/取消时拿不到日志；fan-out 实时写更可靠。

**目录结构**：
```
/opt/deploy-agent/log/
├── agent-2026-08-10.log      # daily，所有 task 混写（不变）
└── tasks/
    ├── {uuid1}.log           # per-task
    └── {uuid2}.log
```

### D2: per-task logger 通过 context 传递，而非全局

**选择**：`executeTask` 开始时调用 `logging.OpenTaskLog(taskID)` 返回一个 `*os.File`，构造 task-scoped `slog.Logger`（基于 fan-out handler），通过 `context` 传递（扩展现有 `WithTaskID` 模式，新增 `WithTaskLogger`）。`FromContext` 优先用 task logger，否则 fallback 到 default。

**备选**：全局 `logging.SetTaskLogger(taskID, logger)` + 用完 unset。被否：全局状态在并发 task 下易错；context 已是现有 trace 字段的传递方式，一致。

### D3: Server 拉日志用 poll 后顺手 GET，不引入回调

**选择**：`pollTaskStatus` 拿到终态（success/failed/cancelled）后，立即 `GET /api/task/{id}/logs` 拉取该 task 的 per-task 日志文件内容，返回给 `deployToServer`，由 `DeployService.deploy` 聚合。

**备选**：agent task 完成后主动 POST 到 server。被否：agent 当前不知道 `deploy_history.id`（request 里没带），要加字段；且 agent 要配 server 回调地址 + 防火墙开反向端口。改动面大。

**聚合格式**（`detail_logs` 内容，纯文本，按 server 分段）：
```
===== [server-a 192.168.1.10] =====
task start  task_name=deploy step_count=3
step[0] shell "systemctl stop app"  exit=0  120ms
  output: (empty)
step[1] deploy "copy jar"  exit=0  350ms
  output: Successfully deployed artifact app.jar to /opt/app/app.jar
step[2] shell "systemctl start app"  exit=0  2100ms
  output: (empty)
task end  status=success  duration=2600ms

===== [server-b 192.168.1.11] =====
[拉取失败: connection refused]
```

server 端把 agent 返回的原始 JSON 日志行解析后重新格式化成上面的可读文本（也可直接存原始 JSON 行，由前端渲染--见 D5）。

### D4: detail_logs 字段 + 三层 schema 演进机制（双保险 + 增量表结构）

**背景**：探索阶段发现 AGENTS.md 描述的"`spring.sql.init.mode: always`，schema.sql 每次启动都跑"与实际代码不符--`application.yml` 是 `mode: never`，schema.sql 从未被 Spring 执行，实际建表/加列完全靠 `DataMigration.java` 的 `ensureColumnExists`。本 change 顺手修正此长期失效，并建立可持续的 schema 演进机制。

**选择**：三层机制，各司其职，互为兜底：

```
① schema.sql  (mode: never -> always, 修正长期失效)
   CREATE TABLE IF NOT EXISTS 含所有列
   新库：建全表 | 老库：IF NOT EXISTS 跳过

② migration/VNNN__name.sql  (continue-on-error)
   声明式增量 DDL，支持任意表结构变更
   (ADD COLUMN / CREATE INDEX / 约束 / RENAME 等)
   新库：列已存在，ALTER 报错被吞 | 老库：增量变更成功

③ DataMigration.ensureColumnExists  (Java 兜底)
   ApplicationReadyEvent 时 PRAGMA 检查 + ALTER
   仅兜底 ADD COLUMN；索引/约束等靠 ②
```

执行顺序：①②（Spring init，bean 初始化前）-> ③（ApplicationReadyEvent，bean 就绪后）。

具体落地：
- `deploy_history` 新增 `detail_logs TEXT` 列。
- 修正 `application.yml`：`spring.sql.init.mode: never` -> `always`（让 schema.sql 真正生效）；新增 `data-locations: classpath:migration/*.sql` 与 `continue-on-error: true`。
- 新建 `server/src/main/resources/migration/V001__add_detail_logs.sql`，内容 `ALTER TABLE deploy_history ADD COLUMN detail_logs TEXT;`。
- `schema.sql` 的 `CREATE TABLE deploy_history` 同步加 `detail_logs TEXT` 列（新库直接建全）。
- `DataMigration.onApplicationReady` 加一行 `ensureColumnExists("deploy_history", "detail_logs", "TEXT")`（兜底 ①② 的边缘情况）。

**备选**：用 Flyway/Liquibase。被否：本项目无现成 migration 框架，引入过重；`future-architecture-and-pipeline` change 已规划 Flyway，届时②③可统一收敛，本 change 不超前。

**备选**：只走 ③（最小改动，符合现状）。被否：用户明确要"双保险 + 允许增量表结构"；仅靠 ensureColumnExists 只能加列，无法承载索引/约束等增量变更。

**备选**：只走 ①②（不兜底）。被否：① IF NOT EXISTS 不更新老库结构，② 重复 ALTER 靠 continue-on-error 吞错，极端情况（如 ② 文件命名排序错误、Spring init 时序异常）可能漏列；③ 是最后一道防线。

**规约沉淀**：`AGENTS.md` 的 Data & Schema 段改写为实际机制："Schema 演进三层机制：① `schema.sql`（mode: always）放 `CREATE TABLE IF NOT EXISTS` 定义全量列；② `migration/VNNN__name.sql` 放增量 DDL（ALTER/INDEX/约束等，continue-on-error 容错重复执行）；③ `DataMigration.java` 的 `ensureColumnExists` 兜底加列。新增列时三层同步更新。"

### D5: detail_logs 存可读文本，前端按 server 分段渲染

**选择**：server 端把聚合后的日志存为可读文本（D3 的格式），前端按 `===== [server ...] =====` 分隔符切分段落渲染。

**备选**：存结构化 JSON（`[{server, status, lines:[...]}, ...]`），前端 JSON 渲染。被否：detail_logs 是 TEXT 列，存 JSON 要转义；且可读文本在 DB 里直接可读，排查方便。分隔符足够简单稳定。

### D6: Agent 端日志端点返回原始日志行

**选择**：`GET /api/task/{taskId}/logs` 直接返回 `{taskID}.log` 文件内容（每行一个 JSON slog record），Content-Type `text/plain` 或 `application/x-ndjson`。server 端解析后格式化。

**备选**：agent 端格式化成可读文本再返回。被否：agent 不该承担展示职责；server 聚合多 server 时统一格式化更一致。

**边缘**：taskID 不存在或文件不存在 -> 404。文件存在但为空 -> 200 + 空体。

### D7: per-task 文件清理复用 MaxAgeDays，不立即删

**选择**：cleaner 扫描 `tasks/*.log`，按 mtime 判断，超 `MaxAgeDays` 删除。与 `agent-*.log` 用同一阈值、同一周期。

**备选**：server 拉取成功后通知 agent 删除。被否：增加一次往返；时序耦合（agent 要确认 server 存成功）；拉取失败时日志反而没了。30 天保留足够回溯，磁盘压力小。

## Risks / Trade-offs

- **[per-task 文件数量增长]** 高频部署场景 `tasks/` 下文件多。-> 30 天 + cleaner 兜底；单文件通常 KB 级（除非 step output 巨大）。
- **[大 step output 占用 detail_logs]** step 输出 MB 级时，多 server 聚合后 detail_logs 可能几十 MB，SQLite TEXT 列无硬上限但影响查询性能。-> 本次不截断（与 agent 现有"output 不截断"规约一致）；后续可加 `detail_logs_truncated` 标记或外存文件。design 里记为已知限制。
- **[migration continue-on-error 吞掉真实错误]** ALTER 重复列报错被吞，但其他 SQL 错误也被吞。-> migration 文件只放幂等 ALTER，每文件单语句；命名 `VNNN__` 顺序明确；第三层 `DataMigration.ensureColumnExists` 用 PRAGMA 精确检查兜底，即使 ② 失败 ③ 仍能补列；出问题靠日志排查。
- **[mode: never -> always 的行为变更]** 修正后 schema.sql 将真正每次启动执行，此前依赖"schema.sql 不跑"的隐式行为可能暴露。-> schema.sql 全是 `CREATE TABLE IF NOT EXISTS`，对已存在表无破坏性；DataMigration 已有 ensureColumnExists 模式证明加列安全；本变更属于"修正失效"，非引入新行为。
- **[agent 老版本无日志端点]** server 拉 logs 拿 404，该 server 段标 `[agent 版本过低，无日志]`。-> 兼容性已考虑；不阻塞部署本身。
- **[pollTaskStatus 拉日志增加部署完成延迟]** 每个 server 多一次 HTTP 往返。-> 通常 <100ms；且 poll 已是 1s 间隔，可接受。
- **[fan-out writer 中途 task writer 关闭后仍被写]** task 结束关闭 file，但若有残留 goroutine 还在写 -> panic on closed file。-> `TaskWriter.Write` 内部用 mutex + closed 标志，关闭后写丢弃并记一次 error 到 daily。

## Migration Plan

1. **Agent 先发**：新 agent 版本上线后即开始写 per-task 文件、提供 logs 端点。老 server 不调此端点，无影响。
2. **Server 后发**：新 server 上线后，poll 完成时尝试拉 logs；对老 agent（404）标 `[agent 版本过低，无日志]`，不阻塞。migration SQL 自动执行。
3. **前端最后发**：详情 drawer 上线。老 deploy_history 记录 `detail_logs` 为 null，详情页只显示 summary。
4. **回滚**：agent 回滚 -> per-task 文件残留但无用，cleaner 30 天后清；server 回滚 -> migration 列保留（无害），detail_logs 不再写入；前端回滚 -> 详情按钮回到 stub。三者独立可回滚。

## Open Questions

（无--探索阶段已与用户确认全部决策点）
