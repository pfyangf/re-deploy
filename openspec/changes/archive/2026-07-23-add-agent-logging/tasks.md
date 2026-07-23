## 1. Logging 模块基础设施

- [x] 1.1 新建目录 `agent/internal/logging/`
- [x] 1.2 实现 `DailyWriter`（`io.Writer` 包装），跨天时关闭旧文件、打开 `agent-YYYY-MM-DD.log` 新文件，使用互斥锁保护并发写
- [x] 1.3 实现 `logger.go`：`Init(cfg LogConfig) error` 构造 `slog.Logger`，JSON handler + DailyWriter 作为 sink，设置为 `slog.SetDefault`
- [x] 1.4 实现 `context.go`：`WithTaskID`/`WithStepIndex`/`WithUploadID`/`WithRequestID` 以及 `FromContext(ctx) *slog.Logger`（无 ctx 或字段缺失时回退到 default logger）
- [x] 1.5 实现 `cleaner.go`（logging 内部）：启动一个 24h ticker 协程，扫描 `<log.dir>/agent-*.log`，删除 `mtime` 早于 `now - MaxAgeDays` 的文件；`max_age_days=0` 时禁用清理；启动时立即跑一次

## 2. 配置扩展

- [x] 2.1 在 `agent/internal/config/config.go` 新增 `LogConfig` 结构（`Dir` / `Level` / `MaxAgeDays`），挂在 `Config.Log` 字段下
- [x] 2.2 在 `configFile` 中增加对应字段并支持 yaml 反序列化
- [x] 2.3 在 `Load()` 里为 `Log` 段填充默认值（`dir=/opt/deploy-agent/log`, `level=info`, `max_age_days=30`），缺失 `log` 段时全部走默认
- [x] 2.4 `Save()` 保留 `Log` 段写入
- [x] 2.5 `Load()` 中创建 `Log.Dir` 目录（`os.MkdirAll`）

## 3. 接入 main / 执行入口

- [x] 3.1 在 `cmd/agent/main.go` 里 `config.Load()` 之后立刻 `logging.Init(cfg.Log)`
- [x] 3.2 保留 stdout 上的启动 / 停止 / token 首次打印（走 `fmt.Fprintln(os.Stdout, ...)` 或独立 stdout 打印，不进 slog 文件 handler）
- [x] 3.3 fatal 路径（`log.Fatalf` 等）改成 `slog.Error(...)` + `os.Exit(1)` 或在 stdout 单独打一份，确保 journalctl 能看到宕机原因
- [x] 3.4 移除 / 替换 `main.go` 中所有 `log.Printf`

## 4. Executor 改造

- [x] 4.1 修改 `agent/internal/executor/executor.go`：`ExecuteShell` 签名改为 `ExecuteShell(ctx context.Context, command string, timeout int)`，`ExecuteScript` 同步加 `ctx`
- [x] 4.2 在 `ExecuteShell` 内部通过 `logging.FromContext(ctx)` 获取 logger，命令开始 / 结束打 debug 级日志（避免噪音，主日志由 task.go 层负责）
- [x] 4.3 保留把 stdout+stderr 装 `bytes.Buffer` 并返回给调用者的既有行为
- [x] 4.4 超时路径明确返回 timeout error 并附上已捕获的部分 output

## 5. Task Handler 接入日志

- [x] 5.1 修改 `agent/internal/api/task.go` `taskExecuteHandler`：为 request 派生 ctx，`logging.WithTaskID(ctx, execution.ID)`
- [x] 5.2 `executeTask`：进入循环前打 `task.start` 事件（`task_id` / `task_name` / step 数）
- [x] 5.3 每一步进入前打 `task.step.start`（`task_id` / `step_index` / `step_name` / `step_type` / `command` 或 `deploy_path` / `timeout`）
- [x] 5.4 每一步结束后打 `task.step.end`：`exit_code` / `duration_ms` / `status` / **完整 output（不截断）**；失败时 level=`error` 并带 `error` 字段
- [x] 5.5 任务整体结束时打 `task.end`（`status` / `duration_ms` / 错误信息）
- [x] 5.6 调用 `executor.ExecuteShell` / `ExecuteScript` 时传入携带追踪字段的 ctx

## 6. Upload / Register / Router 迁移

- [x] 6.1 `internal/api/upload.go`：`uploadInitHandler` / `uploadCompleteHandler` 打 `upload.init` / `upload.complete` 事件（`upload_id` / `filename` / `size` / `md5_ok`）；替换其余 `log.Printf`
- [x] 6.2 `internal/api/register.go`：`RegisterWithServer` / `sendHeartbeat` 中的 `log.Printf` 改为 `slog`，事件命名 `agent.register.*` / `agent.heartbeat.*`
- [x] 6.3 `internal/api/router.go`：`loggingMiddleware` 生成 `request_id` 挂到 ctx，替换 `log.Printf`，事件 `http.request`（包含 `method` / `path` / `remote`）；将新 ctx 通过 `r.WithContext(...)` 下传
- [x] 6.4 `taskExecuteHandler` 内从 request ctx 继承 `request_id` 后再叠加 `task_id`

## 7. Cleaner 侧调整

- [x] 7.1 `internal/cleaner/cleaner.go` 移除 `cleanupDir(filepath.Join(c.dataDir, "logs"), c.retentionDays)` 那行
- [x] 7.2 保留 uploads / artifacts / scripts 三条清理路径
- [x] 7.3 `Cleaner` 内部的 `log.Printf` 迁移到 `slog`

## 8. Server 侧配套

- [x] 8.1 修改 `server/src/main/java/.../AgentDownloadController.getInstallScript()`，在安装脚本中 `mkdir -p /opt/deploy-agent/log` 并设置 owner / mode 一致于 `/opt/deploy-agent/`
- [x] 8.2 （可选）在安装脚本 systemd unit 或部署文档中提示"运行时日志见 /opt/deploy-agent/log/agent-YYYY-MM-DD.log"

## 9. 验证

- [x] 9.1 本地构建 agent：`go build ./...` 通过；`GOOS=linux GOARCH=amd64 go build -o deploy-agent-linux-amd64 ./cmd/agent` 通过
- [x] 9.2 手动跑：首次启动 → 检查 `/opt/deploy-agent/log/agent-<今天>.log` 存在，JSON 一行一条
- [x] 9.3 手动跑：触发一次多步 task，检查日志包含 `task.start` / `task.step.start` / `task.step.end` / `task.end` 且每条含 `task_id`，`output` 字段完整包含脚本输出
- [x] 9.4 手动跑：故意失败一步，检查 `task.step.end` level=`error` 且 `error` 字段非空
- [x] 9.5 手动跑：`journalctl -u deploy-agent` 里只看到启动 / 停止相关行，无运行时事件刷屏
- [ ] 9.6 手动跑：模拟跨天（改本地时间或调低阈值），确认新文件正确创建、旧文件保持完整
- [ ] 9.7 手动跑：把某个 `agent-*.log` 的 mtime 改到 40 天前，等待或手动触发清理，确认被删除
- [x] 9.8 回归：现有 upload / register / heartbeat / task 端到端流程未破坏

## 10. 文档

- [x] 10.1 更新 `AGENTS.md`：把"agent 日志走 systemd journal"改成"生命周期走 journal，运行时走 /opt/deploy-agent/log/agent-YYYY-MM-DD.log"
- [x] 10.2 更新 `README.md` / `docs/` 中 agent 排障相关的说明（如有）
