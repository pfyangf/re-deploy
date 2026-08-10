# 构建与部署指南

本文覆盖 re-deploy server 的**发版流程**（如何产出新版本 Docker 镜像）与**部署方式**（Docker Compose / docker run / 本地开发）。

Agent 的安装通过服务端页面下载后运行安装脚本完成，不在本文范围。

---

## 1. 版本号规则

### 唯一真源

`server/pom.xml` 中的 `<version>` 是本项目发布版本的**唯一来源**。所有产物都从它派生：

| 产物 | 来源 |
|------|------|
| Spring Boot fat jar 文件名 | `server/target/redeploy-server-<version>.jar` |
| Docker 镜像 tag | `pengfei2022/redeploy-server:<version>` |
| Git tag | `v<version>` |

绝不允许在其他地方硬编码版本号。所有脚本通过 `mvn help:evaluate -Dexpression=project.version -q -DforceStdout` 读取。

### SNAPSHOT 生命周期

master 分支 HEAD 上的 pom 版本**始终**是 `X.Y.Z-SNAPSHOT` 形式（表示"下一版开发中"）。仅在 release 脚本执行的短暂窗口内脱离 SNAPSHOT。

```
0.1.0-SNAPSHOT ──release patch──► 0.1.0（打 tag、推 Docker Hub）──► 0.1.1-SNAPSHOT
```

### bump 规则

| 参数 | 含义 | 示例：从 0.1.5-SNAPSHOT 起 |
|------|------|-----------------------------|
| `patch` | 只去掉 -SNAPSHOT | → 0.1.5，next 0.1.6-SNAPSHOT |
| `minor` | 中间号 +1，末尾归零 | → 0.2.0，next 0.2.1-SNAPSHOT |
| `major` | 首号 +1，其余归零 | → 1.0.0，next 1.0.1-SNAPSHOT |
| `<X.Y.Z>` | 显式指定版本号 | → X.Y.Z，next X.Y.(Z+1)-SNAPSHOT |

---

## 2. 发版流程

### 前置准备（一次性）

```bash
# 1. Docker Hub 登录（用户名固定 pengfei2022）
docker login -u pengfei2022

# 2. 初始化 buildx builder（用于多架构构建）
docker buildx create --use --name redeploy-builder

# 3. 确认工具版本
mvn -v          # 3.8+
go version      # 1.21+
docker version  # 20.10+，需支持 buildx
```

### 两种脚本

| 脚本 | 用途 | 会不会推 Docker Hub | 会不会改 git |
|------|------|---------------------|-------------|
| `scripts/build.{ps1,sh}` | 本地构建，验证镜像可跑 | 否 | 否 |
| `scripts/release.{ps1,sh}` | 正式发版 | 是（multi-arch） | 是（commit + tag + push） |

### build.sh / build.ps1

```bash
# Linux/macOS
./scripts/build.sh                                    # 默认：从 pom 读版本，单平台 amd64，本地 --load
./scripts/build.sh --version 0.1.1                    # 指定版本号
./scripts/build.sh --platforms linux/arm64            # 只构 arm64
./scripts/build.sh --no-agent                         # 跳过 agent 交叉编译
```

```powershell
# Windows
./scripts/build.ps1
./scripts/build.ps1 -Version 0.2.3
./scripts/build.ps1 -Platforms linux/arm64
./scripts/build.ps1 -NoAgent
```

build 脚本会执行：
1. 检查 mvn / go / docker / buildx 可用
2. 交叉编译 agent 到 `server/data/agents/deploy-agent-linux-{amd64,arm64}`
3. `mvn -DskipTests clean package`
4. `docker buildx build --load`（单平台）或 `--output cacheonly`（多平台不推）

### release.sh / release.ps1

```bash
# Linux/macOS
./scripts/release.sh patch          # 常规小版本发布
./scripts/release.sh minor
./scripts/release.sh major
./scripts/release.sh 0.5.0          # 显式版本
```

```powershell
# Windows
./scripts/release.ps1 patch
./scripts/release.ps1 0.5.0
```

release 脚本会依次完成：
1. 校验 git 干净 + docker buildx + docker login
2. `mvn versions:set` 到目标版本（去掉 SNAPSHOT）
3. 调用 build 脚本，`--push` multi-arch（linux/amd64 + linux/arm64）到 Docker Hub
4. `git commit "release: v<版本>"` + `git tag v<版本>`
5. `mvn versions:set` 到下一版 `-SNAPSHOT`
6. `git commit "chore: bump to <下一版>-SNAPSHOT"`
7. `git push --follow-tags`
8. 打印验证命令

失败会给出回滚提示（`git reset --hard HEAD~N`、`git tag -d v<版本>`）。

### 首次发版

```bash
# 当前 pom 是 0.1.0-SNAPSHOT（本 change 落地时归零结果）
./scripts/release.sh patch

# 完成后：
# - Docker Hub: pengfei2022/redeploy-server:0.1.0 + :latest（含 amd64 + arm64）
# - Git tag:    v0.1.0
# - pom:        0.1.1-SNAPSHOT
```

验证：

```bash
docker buildx imagetools inspect pengfei2022/redeploy-server:0.1.0
# 应看到 linux/amd64 与 linux/arm64 两条 manifest
```

---

## 3. Docker Compose 部署（推荐）

### 3.1 准备环境变量

```bash
cp .env.example .env
vi .env
```

至少必须修改 `REDEPLOY_ADMIN_TOKEN`（默认 `changeme` 不安全）。

### 3.2 启动

```bash
docker compose pull
docker compose up -d
```

### 3.3 查看日志

```bash
docker compose logs -f redeploy-server
```

### 3.4 停止 / 升级

```bash
docker compose down                         # 停止
docker compose pull && docker compose up -d # 升级到最新 :latest
```

### 3.5 数据卷位置

| 容器路径 | 宿主路径 | 说明 |
|---------|---------|------|
| `/app/data` | `./data` | SQLite 数据库、上传文件缓存、agent 二进制分发目录 |
| `/app/logs` | `./logs` | Spring Boot 应用日志（redeploy-server.log） |

**升级不会丢数据**：SQLite 和上传文件都在 `./data` 卷内。

---

## 4. docker run 单容器部署

如果不想用 compose：

```bash
docker run -d \
  --name redeploy-server \
  --restart unless-stopped \
  -p 9006:9006 \
  -v $(pwd)/data:/app/data \
  -v $(pwd)/logs:/app/logs \
  -e TZ=Asia/Shanghai \
  -e REDEPLOY_ADMIN_TOKEN="替换成强随机值" \
  -e REDEPLOY_SSH_ENCRYPTION_KEY="" \
  -e REDEPLOY_DINGTALK_ENABLED=false \
  pengfei2022/redeploy-server:latest
```

---

## 5. 本地开发（不用 Docker）

适合调试代码。

### 5.1 交叉编译 agent（首次运行前必做）

`AgentDownloadController` 分发的 agent 二进制来自 `server/data/agents/`。本地跑之前需要放好：

```bash
# Linux/macOS/WSL
cd agent
GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -o ../server/data/agents/deploy-agent-linux-amd64 ./cmd/agent
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o ../server/data/agents/deploy-agent-linux-arm64 ./cmd/agent
```

```powershell
# Windows PowerShell
cd agent
$env:GOOS="linux"; $env:GOARCH="amd64"; $env:CGO_ENABLED="0"
go build -o ../server/data/agents/deploy-agent-linux-amd64 ./cmd/agent
$env:GOARCH="arm64"
go build -o ../server/data/agents/deploy-agent-linux-arm64 ./cmd/agent
Remove-Item Env:GOOS, Env:GOARCH, Env:CGO_ENABLED
```

或直接跑一次 `./scripts/build.{ps1,sh}`，会自动放好。

### 5.2 设置环境变量

**Windows PowerShell**：

```powershell
$env:REDEPLOY_ADMIN_TOKEN = "my-strong-token"
$env:REDEPLOY_SSH_ENCRYPTION_KEY = ""     # 空 → 首启自动生成，看控制台
```

**Linux / macOS bash**：

```bash
export REDEPLOY_ADMIN_TOKEN="my-strong-token"
export REDEPLOY_SSH_ENCRYPTION_KEY=""
```

不设置也可以，`application.yml` 里的默认值会生效（`admin-token=changeme` 只适合本地调试）。

### 5.3 启动

```bash
cd server
mvn spring-boot:run
# 或
mvn -DskipTests package
java -jar target/redeploy-server-*.jar
```

访问 http://localhost:9006

---

## 6. 环境变量清单

| 变量 | 含义 | 默认值 | 是否必填 |
|------|------|--------|---------|
| `REDEPLOY_ADMIN_TOKEN` | 运维 Token（Jenkins 等调用 `/api/deploy` 时使用） | `changeme` | Compose 部署必填 |
| `REDEPLOY_SSH_ENCRYPTION_KEY` | SSH 私钥 AES 加密密钥（Base64，16/24/32 字节） | 空 → 首启自动生成 | 否 |
| `REDEPLOY_DINGTALK_ENABLED` | 是否启用钉钉告警 | `false` | 否 |
| `REDEPLOY_DINGTALK_WEBHOOK_URL` | 钉钉机器人 webhook URL | 空 | 启用钉钉时必填 |
| `REDEPLOY_DINGTALK_NOTIFY_MODE` | 通知模式：`failure-only` 或 `all` | `failure-only` | 否 |
| `REDEPLOY_LOG_RETENTION_DAYS` | Agent 日志保留天数 | `7` | 否 |
| `REDEPLOY_HISTORY_RETENTION_DAYS` | 部署历史保留天数 | `7` | 否 |
| `REDEPLOY_UPLOAD_DIR` | 上传文件目录 | `./data/uploads` | 否 |
| `REDEPLOY_AGENT_DIR` | Agent 二进制分发目录 | `./data/agents` | 否 |
| `JAVA_OPTS` | JVM 参数 | `-Xms256m -Xmx512m` | 否 |
| `TZ` | 时区 | `Asia/Shanghai` | 否 |

---

## 7. 常见故障排查

### 7.1 `docker buildx: command not found` 或 buildx 未初始化

```bash
docker buildx create --use --name redeploy-builder
docker buildx inspect --bootstrap
```

### 7.2 release 脚本报 "未检测到 docker login 状态"

```bash
docker login -u pengfei2022
# 输入 Docker Hub 密码或 Access Token
```

Windows 下需要 Docker Desktop 已启动并登录。

### 7.3 UI 下载 agent 二进制 404

**症状**：服务端管理页面点击"下载 Agent"跳出 404。

**原因**：`server/data/agents/deploy-agent-linux-<arch>` 缺失。

- Docker 部署：说明镜像里没打进去，检查 build 脚本是否成功执行了 agent 交叉编译；或者卷挂载 `./data:/app/data` 时被空的宿主目录覆盖了 —— 首次启动请让容器自己创建 `data` 目录，或手工把 agent 二进制放到宿主 `./data/agents/`。
- 本地开发：跑 §5.1 的交叉编译命令。

### 7.4 `REDEPLOY_ADMIN_TOKEN=changeme` 生产环境告警

`changeme` 只是占位默认。生产环境部署前务必在 `.env` 或环境变量中设置为强随机值：

```bash
openssl rand -base64 32
```

### 7.5 首次启动 SSH 密钥丢失

如果没有设置 `REDEPLOY_SSH_ENCRYPTION_KEY`，容器**首次启动**会生成一个并打印到日志。请从日志中记录下来，写回 `.env`，避免容器重建后丢失导致旧数据无法解密。

```bash
docker compose logs redeploy-server | grep -i "ssh.*key\|encryption"
```

### 7.6 端口 9006 被占用

修改 `docker-compose.yml`：

```yaml
ports:
  - "18006:9006"   # 宿主 18006 → 容器内 9006
```

或 `docker run` 时改 `-p 18006:9006`。

### 7.7 release 中途失败如何回滚

脚本会在失败时打印回滚提示。手工场景：

```bash
# 查看当前状态
git log --oneline -5
git tag --list

# 回退 release 相关 commit（假设产生了 2 个 commit：release + chore）
git reset --hard HEAD~2

# 删除本地和远端 tag
git tag -d v<版本>
git push -d origin v<版本>

# Docker Hub 上的 tag 无法通过脚本回滚，需要到 https://hub.docker.com/r/pengfei2022/redeploy-server/tags 手工删除
```
