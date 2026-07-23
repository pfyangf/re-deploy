## Context

Agent 侧现在的日志全部走标准库 `log` 包直接输出到 stdout，最终被 systemd 收到 journalctl 里。`executor.ExecuteShell` 把命令输出装进 `bytes.Buffer` 返回后就丢弃，没有落盘。`cleaner.go` 已经在扫描 `data/logs/` 做过期清理，但这是个从未被写入的幽灵目录。

现状约束：
- Go 版本已锁定在 1.21（`agent/go.mod`），可以直接用标准库 `log/slog`
- Agent 是无 QPS 压力的边车进程，日志性能不是瓶颈
- Agent 运行在托管的 Linux 服务器上（有 root，`/opt/deploy-agent/` 是主目录）
- 部署流程简单，脚本 output 量级可控，用户明确要求**完整写入**不截断
- 用户明确要求 **只用官方库 / 标准库**，不引入 lumberjack 等第三方依赖
- Journalctl 保持"生命周期日志"的定位（启动、停止、fatal / panic）

## Goals / Non-Goals

**Goals:**
- 使用 `log/slog` 标准库构建结构化 JSON 日志
- 主日志文件按天切分，文件名格式 `agent-YYYY-MM-DD.log`
- 部署脚本 / 命令的完整 stdout+stderr 落盘（不截断）
- 每条日志携带 `task_id` / `step_index` / `upload_id` 等追踪字段（可用时）
- 通过 `context.Context` 传播追踪字段，避免逐层显式传参
- 过期日志自动清理，默认保留 30 天，天数可配
- 零第三方日志依赖（不引入 lumberjack / zap / zerolog）
- Journalctl 只保留生命周期事件（启动 / 停止 / fatal）

**Non-Goals:**
- 不做每任务独立 output 文件（用户选择方案 A：主日志汇流）
- 不做日志级别的运行时热更新（重启生效即可）
- 不与 server 侧建立日志上传通道（本地文件足够）
- 不处理 `task_id` ↔ `upload_id` 的跨阶段串联（当前 API 未下发关联 ID）
- 不做 output 截断 / 采样（完整写入）
- 不做单文件大小上限（纯按天切，若某天异常暴涨由 disk quota / 保留期兜底）

## Decisions

### D1：使用 `log/slog` + 自研 DailyWriter，不引入 lumberjack

**选择**：Go 1.21 标准库 `log/slog`（JSON handler）+ 一个约 40 行的 `DailyWriter`（`io.Writer` 包装）。

**理由**：
- 用户明确"用官方库"，标准库优先
- Agent 无高并发日志压力，`slog` 的默认性能完全够用
- `DailyWriter` 逻辑极简：写入前比较当前日期 vs 目标文件日期，跨天则关闭旧文件、打开新文件
- 少一个依赖等于少一次供应链风险 + 少一次 `go.sum` 变更

**候选对比**：
- `lumberjack`：功能全但按大小 + 时间双轴，与"纯按天"语义不完全匹配，且引入第三方依赖
- `logrus`：官方已停止开发（maintenance）
- `zap` / `zerolog`：性能好但 agent 用不到，过度设计

### D2：日志目录 `/opt/deploy-agent/log/`，脱离 `data/`

**选择**：主日志目录 `/opt/deploy-agent/log/`，与 `data/`（存放 artifact / upload chunk / script 临时文件）平级。

**理由**：
- 用户明确要求"新家"路径
- 日志有独立的清理生命周期（按天数），不宜和 artifact / script（按内容过期）混在一起
- 运维 `tail -f` / `logrotate` 集成时目录清晰

**副作用**：`cleaner.go` 里 `cleanupDir(filepath.Join(c.dataDir, "logs"), ...)` 那行删掉（本来就是幽灵）。

### D3：追踪字段通过 `context.Context` 传播

**选择**：新增 `logging.WithTaskID(ctx, id)` / `logging.FromContext(ctx)` 辅助函数。executor / task handler 内的 logger 从 ctx 派生。

**理由**：
- 避免 `ExecuteShell(command, timeout, taskID, stepIdx, ...)` 参数列表爆炸
- Go 惯例（`net/http.Request.Context()`、`database/sql` 都是 ctx-first）
- 后续如果加 `request_id` / `trace_id` 只需在 ctx 里挂一个新键，调用点不用改

**代价**：`ExecuteShell` / `ExecuteScript` 签名需要加 `ctx context.Context` 首参 —— 但 executor 只被 `task.go` 一处调用，改动局部化。

### D4：文件级别 DEBUG+，Journalctl 只出生命周期

**选择**：
- 文件 handler：全量（INFO 及以上默认打开，DEBUG 通过配置开启）
- Stdout handler：仅 agent 启动 / 停止 / token 首次打印 / fatal（直接用 `fmt.Fprintln(os.Stdout, ...)` 或独立 slog handler + 只放行 `event=agent.*` / `level>=ERROR`）

**理由**：
- 用户明确"journal 只写基础的启动停止"
- 保留 fatal / panic 输出到 stdout，方便 systemd 层看到宕机原因

**实现细节**：单独构造两个 `slog.Handler`，用 `slog.NewMultiHandler`（或自写 `multiHandler`）合并，或者干脆 stdout 只保留 `log.Println("Deploy Agent started ...")` 这类明显生命周期打印，其他事件全部只走 file handler。倾向后者，简单直接。

### D5：日志事件命名与字段约定

**事件命名**：`<domain>.<action>` 形式，如 `agent.start` / `task.step.start` / `task.step.end` / `upload.init` / `upload.complete` / `http.request`。

**通用字段**：`ts` / `level` / `event` / `msg`
**任务相关**：`task_id` / `task_name` / `step_index` / `step_name` / `step_type` / `exit_code` / `duration_ms` / `output` / `error`
**上传相关**：`upload_id` / `filename` / `size` / `md5_ok`
**HTTP 相关**：`method` / `path` / `remote` / `status`

`output` 字段完整承载脚本 stdout+stderr（不截断）。

### D6：清理由 logging 模块自管，不复用 cleaner

**选择**：logging 模块启动一个 24h ticker 的清理协程，扫描 `/opt/deploy-agent/log/agent-*.log`，删除 `mtime` 早于 `now - MaxAgeDays` 的文件。

**理由**：
- `cleaner.go` 现有逻辑针对 `data/` 子目录，把 log 塞进去会耦合
- Logging 自管路径 + 清理策略，模块内聚

### D7：配置字段与默认值

`config.yaml` 追加：
```yaml
log:
  dir: /opt/deploy-agent/log      # 默认值
  level: info                     # debug|info|warn|error
  max_age_days: 30                # 保留天数，0 表示不清理
```

未提供时全部走默认值，向后兼容既有 `config.yaml`。

## Risks / Trade-offs

- **[磁盘暴涨]**：某次异常脚本疯狂输出（比如无限 log 循环）会撑爆磁盘 → **Mitigation**：靠 30 天保留 + 未来加"单文件大小报警"的运维监控；本 change 不做单文件封顶（保持"按天"语义纯粹）
- **[跨天丢失切换瞬间日志]**：`DailyWriter` 切换文件是在下一次 `Write` 前做，如果切换瞬间程序 crash 可能损失极少量日志 → **Mitigation**：切换用互斥锁保护，正常情况不丢；agent crash 由 journalctl 兜底
- **[Ctx 未传播]**：如果未来有人在 executor 里 `go func()` 开协程忘了带 ctx，会丢失 task_id → **Mitigation**：logger 派生统一走 `logging.FromContext`，无 ctx 时回退到根 logger（不会 panic，最坏是少一个字段）
- **[Output 完整写入放大日志体积]**：一次大 npm build 可能几 MB output，30 天累积可观 → **Mitigation**：用户明确"部署流程简单"，先按当前需求做；后续可加"单条 output 超阈值时降级"配置
- **[Journalctl 变得过于安静]**：运维习惯 `journalctl -u deploy-agent` 排查问题，改后看不到运行时事件 → **Mitigation**：文档明确指引 `tail -f /opt/deploy-agent/log/agent-$(date +%F).log`；启动时在 stdout 打印一行"运行时日志见 /opt/deploy-agent/log/"

## Migration Plan

- 无数据迁移（新建目录、新配置字段有默认值）
- 老 agent 升级步骤：
  1. `AgentDownloadController.getInstallScript()` 更新，包含 `mkdir -p /opt/deploy-agent/log`
  2. 老实例通过 `/api/agent/update` 触发自升级（现有能力）
  3. 首次启动写入新格式日志到新目录
  4. 老的 `data/logs/`（如果存在）由后续任意一次 cleaner 忽略即可（本 change 里 cleaner 已不扫它）
- 回滚：直接换回旧二进制；新配置 `log.*` 段被忽略（YAML unknown field），无副作用

## Open Questions

- 是否需要在 stdout 保留极简的 "task X started" / "task X finished" 类进度提示，方便 `journalctl -f` 观察？倾向**不加**（journal 保持只出生命周期），运行时观察走 `tail -f` 日志文件。
- Windows agent 目录约定？—— 当前 agent 主战场是 Linux（`GOOS` 判定），Windows 下 `/opt/deploy-agent/log/` 不合法。倾向：**Windows 场景不在本 change 范围**，或用 `filepath.Join` + 环境变量 `AGENT_LOG_DIR` 兜底（已通过 `config.log.dir` 配置字段覆盖）。
