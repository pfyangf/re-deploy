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

```bash
# Server (from server/)
mvn package                        # produces target/redeploy-server-1.0.0.jar
mvn spring-boot:run                # dev run
java -jar target/redeploy-server-1.0.0.jar

# Agent (from agent/)
go build -o deploy-agent ./cmd/agent

# Cross-compile agents that the server distributes via /api/agent/download/{os}/{arch}
GOOS=linux GOARCH=amd64 go build -o deploy-agent-linux-amd64 ./cmd/agent
GOOS=linux GOARCH=arm64 go build -o deploy-agent-linux-arm64 ./cmd/agent
```

**Non-obvious**: `AgentDownloadController` serves binaries from `redeploy.agent-dir` (default `./data/agents/` relative to server CWD). After cross-compiling, drop binaries there with exact names `deploy-agent-<os>-<arch>` (e.g. `deploy-agent-linux-amd64`) or the download 404s. There is no automated pipeline for this.

## Tests / Lint

- No tests exist yet. `server/src/test/` is empty; `openspec/changes/re-deploy-tool/tasks.md` section 13 is unchecked.
- No lint / formatter / typecheck config. No CI workflows.
- `mvn test` and `go test ./...` are safe to run but currently no-ops.

## Data & Schema

- SQLite DB at `server/data/redeploy.db` (auto-created via `DataDirInitializer`).
- Schema is re-applied on **every** server startup (`spring.sql.init.mode: always`, `classpath:schema.sql`). All statements must stay `CREATE TABLE IF NOT EXISTS` compatible; do not put destructive DDL in `schema.sql`.
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

## Repo conventions

- Chinese comments and docstrings are the norm; keep that style when editing existing files.
- Timezone in Jackson is fixed to `Asia/Shanghai`.
- Server logs → `./logs/redeploy-server.log`; agent logs → systemd journal (`journalctl -u deploy-agent`).
- When adding a new capability, follow the OpenSpec flow: propose under `openspec/changes/<name>/` first; check `openspec/changes/re-deploy-tool/` for the reference structure.
