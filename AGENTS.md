# AGENTS.md

Chinese-language project. Prefer Chinese in user-facing strings, docs, and commit messages. Code and identifiers stay English.

## Structure

Two independent components; there is **no root build file**. Build them separately.

- `server/` — Spring Boot 3 + Java 17 + MyBatis + SQLite. Serves REST API, static Web UI (`src/main/resources/static/`), and hosts agent binaries for download. Port **9006**.
- `agent/` — Go 1.21 module (`github.com/redeploy/agent`). Runs on target Linux servers. Port **9009**. Entrypoint `agent/cmd/agent/main.go`.
- `docs/`, `README.md` — Chinese docs.
- `openspec/` — spec-driven change proposals. Workflow is driven by `/opsx-propose`, `/opsx-apply`, `/opsx-archive`, `/opsx-explore` slash commands defined in `.opencode/commands/`.
- `.archon/`, `.agents/`, `.claude/`, `.opencode/` — agent tooling only; not part of the shipped product.

## Build / Run

**推荐入口**（一键完成 agent 交叉编译 + server 打包 + Docker 镜像）：

```bash
# 本地构建（不推送 Docker Hub、不改 git）
./scripts/build.sh                     # Linux/macOS
./scripts/build.ps1                    # Windows

# 正式发版（bump pom + build + push Docker Hub multi-arch + git commit/tag/push）
./scripts/release.sh patch|minor|major|<X.Y.Z>
./scripts/release.ps1 patch|minor|major|<X.Y.Z>
```

详细流程 & 前置条件（`docker login`、`docker buildx`、环境变量清单）见 [`docs/guide/deployment.md`](docs/guide/deployment.md)。

**手工方式**（不用脚本，用于快速调试）：

```bash
# Server (from server/)
mvn package                        # produces target/redeploy-server-<version>.jar
mvn spring-boot:run                # dev run
java -jar target/redeploy-server-<version>.jar

# Agent (from agent/)
go build -o deploy-agent ./cmd/agent

# Cross-compile agents that the server distributes via /api/agent/download/{os}/{arch}
GOOS=linux GOARCH=amd64 go build -o deploy-agent-linux-amd64 ./cmd/agent
GOOS=linux GOARCH=arm64 go build -o deploy-agent-linux-arm64 ./cmd/agent
```

**Non-obvious**: `AgentDownloadController` 从 `redeploy.agent-dir`（默认 `./data/agents/` 相对于 server CWD）读取二进制。手工交叉编译后请以精确名 `deploy-agent-<os>-<arch>`（如 `deploy-agent-linux-amd64`）放入该目录，否则下载 404。`scripts/build.{ps1,sh}` 会自动处理这一步。

**版本号真源**：`server/pom.xml` `<version>`。master HEAD 始终为 `X.Y.Z-SNAPSHOT`；release 脚本负责去 SNAPSHOT → 打 tag → bump 回 SNAPSHOT。所有 Docker 镜像 tag 与 Git tag 都从 pom 派生，不允许硬编码。

## Tests / Lint

- No tests exist yet. `server/src/test/` is empty; `openspec/changes/re-deploy-tool/tasks.md` section 13 is unchecked.
- No lint / formatter / typecheck config. No CI workflows.
- `mvn test` and `go test ./...` are safe to run but currently no-ops.

## Data & Schema

- SQLite DB at `server/data/redeploy.db` (auto-created via `DataDirInitializer`).
- Schema 演进三层机制（互为兜底）：
  - **① `schema.sql`**（`spring.sql.init.mode: always`）放 `CREATE TABLE IF NOT EXISTS`，定义全量列；新库建全表，老库 IF NOT EXISTS 跳过。
  - **② `migration/VNNN__name.sql`** 放增量 DDL（`ALTER TABLE ADD COLUMN` / `CREATE INDEX` / 约束等），`spring.sql.init.continue-on-error: true` 容错重复执行；新库列已存在报错被吞，老库增量变更成功。
  - **③ `DataMigration.java`** 的 `ensureColumnExists` 在 `ApplicationReadyEvent` 时用 `PRAGMA table_info` 检查 + `ALTER TABLE ADD COLUMN` 兜底（仅加列）。
  - 新增列时三层同步更新。`schema.sql` 只放 `CREATE TABLE IF NOT EXISTS`，不放手写 ALTER。
- MyBatis mappers are **annotation-based** in `repository/*Mapper.java`. `mapper-locations: classpath:mapper/*.xml` is configured but no XML mappers exist — keep new mappers annotation-based unless you also add the XML directory.
- `map-underscore-to-camel-case: true` — DB columns are snake_case, models are camelCase.

## Auth model (easy to confuse)

Two independent bearer tokens:
- `redeploy.admin-token` in `application.yml` — used by Jenkins / external callers hitting the **server**.
- Per-agent token auto-generated on **first agent run** and persisted to `/opt/deploy-agent/conf/config.yaml` (override dir via `AGENT_CONFIG_DIR` env var). Printed once to agent stdout / journalctl on first boot. Must be registered on the server's server-management page.

## Deploy flow gotchas

- `DeployService` deploys to servers in parallel with a fixed pool of **10** (`Executors.newFixedThreadPool(10)`).
- File push uses `FileTransferService` — 5 MB chunk size, loads the whole file into memory (`Files.readAllBytes`). Watch memory when touching 100–200 MB artifact paths.
- Agent `executor.ExecuteShell` runs commands via `sh -c` on Linux, `cmd /C` on Windows, gated by env `GOOS` — not runtime OS. Do not rely on it for Windows detection.
- The Linux install script is embedded as a Java string literal in `AgentDownloadController.getInstallScript()`. Editing the install flow means editing that method, not `scripts/`.
- Per-task 日志：agent 执行 task 时同时写 `{log.dir}/agent-YYYY-MM-DD.log`（daily，所有 task 混写）和 `{log.dir}/tasks/{taskID}.log`（per-task 独立文件）。`GET /api/task/{taskId}/logs` 返回 per-task 文件内容（ndjson）。server 在 `pollTaskStatus` 拿到终态后拉取各 server 的 task 日志，按 `===== [name host] =====` 分段聚合存入 `deploy_history.detail_logs`，前端详情页按分段渲染。老 agent 无此端点时该段标 `[agent 版本过低，无日志]`。

## Repo conventions

- Chinese comments and docstrings are the norm; keep that style when editing existing files.
- Timezone in Jackson is fixed to `Asia/Shanghai`.
- Server logs → `./logs/redeploy-server.log`; agent lifecycle logs → systemd journal (`journalctl -u deploy-agent`); agent **runtime logs** (structured JSON, one line per event, with `task_id` / `step_index` / `upload_id` / `request_id` fields) → `/opt/deploy-agent/log/agent-YYYY-MM-DD.log` (rotated daily, 30-day retention by default; configurable via `log.dir` / `log.level` / `log.max_age_days` in `config.yaml`).
- When adding a new capability, follow the OpenSpec flow: propose under `openspec/changes/<name>/` first; check `openspec/changes/re-deploy-tool/` for the reference structure.
