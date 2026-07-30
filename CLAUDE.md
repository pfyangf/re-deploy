# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Re-Deploy is an automated deployment platform with three components:

- **server**: Spring Boot 3.2 application (Java 17) exposing REST APIs and serving the web UI on port `9006`.
- **frontend**: Vue 3 + Element Plus + Vite SPA, built into `server/src/main/resources/static/` so the Spring Boot jar serves it.
- **agent**: Go 1.21 HTTP daemon running on target servers (default port `9009`) that executes shell tasks and accepts file uploads from the server.

The server orchestrates deployments by calling agents over HTTP in parallel and polling for task status.

## Common Commands

### Local Development

```bash
# 1. Cross-compile agent binaries (required before first server start)
cd agent
GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -o ../server/data/agents/deploy-agent-linux-amd64 ./cmd/agent
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o ../server/data/agents/deploy-agent-linux-arm64 ./cmd/agent

# 2. Start the Spring Boot server
cd server
mvn spring-boot:run
# Or build and run the jar
mvn -DskipTests package
java -jar target/redeploy-server-*.jar
```

Default admin token is `changeme` (override via `REDEPLOY_ADMIN_TOKEN`).

### Frontend Development

```bash
cd frontend
npm run dev      # Vite dev server on port 3000, proxies /api and /ws to localhost:9006
npm run build    # Outputs to ../server/src/main/resources/static/
```

### Full Build (Agent + Server Jar + Docker Image)

```bash
# Linux / macOS
./scripts/build.sh

# Windows
./scripts/build.ps1
```

Build script steps: cross-compile agents to `server/data/agents/`, run `mvn -DskipTests clean package`, then `docker buildx build`. Do not run `docker build .` directly; the Dockerfile expects the jar and agent binaries to already exist.

### Release

Version source of truth is `server/pom.xml <version>`. Release scripts bump the pom, build multi-arch Docker images, push to Docker Hub, commit, tag, and push.

```bash
./scripts/release.sh patch
./scripts/release.sh minor
./scripts/release.sh major
./scripts/release.sh 0.5.0
```

Prerequisites: clean git working tree, docker buildx builder created, and `docker login -u pengfei2022`.

### Docker Compose Deployment

```bash
cp .env.example .env
# Edit REDEPLOY_ADMIN_TOKEN to a strong random value
docker compose up -d
```

Data is persisted in `./data` (SQLite DB, uploads, agent binaries) and `./logs`.

## Architecture

### Server (`server/`)

- **Entry point**: `src/main/java/com/redeploy/RedeployApplication.java`
- **Web layer**: Controllers under `com.redeploy.controller` expose `/api/*` endpoints.
- **Persistence**: SQLite (`./data/redeploy.db`) accessed via MyBatis. Schema is in `src/main/resources/schema.sql`; mappers are in `src/main/resources/mapper/`.
- **Auth**: `WebConfig.AuthInterceptor` guards `/api/**` with the `redeploy.admin-token`. Excluded paths include agent-facing endpoints (`/api/agents/register`, `/api/agents/heartbeat`, `/api/agent/download/**`, `/api/agent/install.sh`) and `/api/health`.
- **Deployment flow**: `DeployController` creates a `DeployHistory` record, then `DeployService.deploy()` fans out to target servers in parallel. Each server is contacted at `http://<host>:<port>/api/task/execute`; the server polls `http://<host>:<port>/api/task/{taskId}/status` until the task completes or times out.
- **Agent distribution**: `AgentDownloadController` serves agent binaries from `server/data/agents/`; the install script is also generated/served from there.
- **Bastion terminal**: `BastionWebSocketHandler` provides browser-based SSH via WebSocket using JSch; credentials are stored encrypted with `REDEPLOY_SSH_ENCRYPTION_KEY`.
- **Static UI**: The Vue frontend build output lives in `server/src/main/resources/static/` and is served by Spring Boot's default static resource handler.

### Agent (`agent/`)

- **Entry point**: `cmd/agent/main.go`
- **HTTP routes**: `internal/api/router.go` sets up authenticated endpoints under `/api/*`:
  - `/api/health` (unauthenticated)
  - `/api/info`
  - `/api/upload/*` chunked file upload
  - `/api/task/execute`, `/api/task/{taskId}/status`, `/api/task/{taskId}/cancel`
- **Task execution**: `internal/executor/executor.go` runs shell commands and script templates with `{{param}}` replacement using `sh -c` on Linux.
- **Configuration**: Loaded by `internal/config/config.go` from `/opt/deploy-agent/conf/config.yaml`. On first run the agent generates a token and prints it to stdout (visible via `journalctl`).

### Frontend (`frontend/`)

- **Entry**: `src/main.js` mounts the Vue app with Pinia, Vue Router, and Element Plus.
- **API client**: `src/api/client.js` uses Axios, reads `VITE_API_BASE`, stores the admin token in `localStorage`, and prompts on 401.
- **Build integration**: `vite.config.js` builds into `../server/src/main/resources/static/`.

## Configuration

Server configuration is in `server/src/main/resources/application.yml`. Important settings and their environment variable overrides:

| Config | Env var | Default | Note |
|---|---|---|---|
| `redeploy.admin-token` | `REDEPLOY_ADMIN_TOKEN` | `changeme` | Required for `/api/deploy` and web UI auth. |
| `redeploy.ssh-encryption-key` | `REDEPLOY_SSH_ENCRYPTION_KEY` | empty | Base64 AES key; auto-generated on first start if empty. Save the generated value. |
| `redeploy.upload-dir` | `REDEPLOY_UPLOAD_DIR` | `./data/uploads` | Artifact uploads. |
| `redeploy.agent-dir` | `REDEPLOY_AGENT_DIR` | `./data/agents` | Agent binaries served to target servers. |
| `redeploy.log-retention-days` | `REDEPLOY_LOG_RETENTION_DAYS` | `7` | Agent log retention. |
| `redeploy.history-retention-days` | `REDEPLOY_HISTORY_RETENTION_DAYS` | `7` | Deploy history retention. |
| `redeploy.dingtalk.*` | `REDEPLOY_DINGTALK_*` | disabled | DingTalk failure alerts. |

## Data Layout

- `./data/redeploy.db` — SQLite database.
- `./data/uploads/` — Uploaded artifacts and Jenkins downloads.
- `./data/agents/` — `deploy-agent-linux-amd64` and `deploy-agent-linux-arm64` binaries distributed to agents.
- `./logs/` — Server log file `redeploy-server.log`.

## Release & Versioning

- The single source of truth for the release version is `server/pom.xml <version>`.
- `master` HEAD should normally be `X.Y.Z-SNAPSHOT`.
- Docker image repository is `pengfei2022/redeploy-server`.
- `scripts/build.{sh,ps1}` are kept in sync; change one, update the other.

## Notes

- There are no automated tests in this repository yet.
- The Dockerfile uses a Huawei Cloud Docker Hub mirror (`swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/...`).
- The Vue `src/main.ts` file is a leftover Vite template; the real entry is `src/main.js`.
