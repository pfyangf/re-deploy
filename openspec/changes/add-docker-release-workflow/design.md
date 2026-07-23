## Context

re-deploy 是单人 / 小团队维护的自部署工具，没有 CI，没有制品仓，也没有多环境发布流水。当前"发版"是手工 `mvn package` + 手工写 commit message 声明版本，`pom.xml` 硬编码 `1.0.0` 且长期不动。Server 分发 agent 二进制依赖 `server/data/agents/deploy-agent-<os>-<arch>` 文件按精确命名存在，一旦漏放某个 arch 就会 404。

杨希望在不引入 CI 的前提下，把发版固化为**一条本地脚本**，保证：
- Maven 版本号是唯一真源，Docker 镜像 tag 与 pom 版本 100% 一致
- 一次动作把 server + 所有需要分发的 agent arch 一并打好
- 镜像推 Docker Hub `pengfei2022/redeploy-server`，multi-arch
- 敏感配置在镜像外通过环境变量注入，镜像本身可公开

约束：
- Windows 本地开发（PowerShell）+ Linux 服务器（bash）都要能跑 → 脚本双份
- 项目根**没有** build 文件，`server/` 与 `agent/` 各自独立，脚本必须自己处理跨目录调用
- 不引入 Nexus / Artifactory；不引入 `maven-release-plugin`（对单人节奏过重）

## Goals / Non-Goals

**Goals:**
- Maven `pom.xml` 版本是发布版本的唯一来源，Docker 镜像 tag 直接读它，两者不可能漂移
- `./scripts/build.{ps1,sh}` 一步完成"agent 交叉编译 + server 打包 + Docker 镜像构建（本地）"
- `./scripts/release.{ps1,sh} [patch|minor|major|<X.Y.Z>]` 完成"bump 版本 → build → push Docker Hub → git commit + tag + push → 回滚 pom 到下一版 -SNAPSHOT"，全自动、原子
- Docker 镜像多架构（linux/amd64 + linux/arm64）
- 敏感配置由环境变量覆盖，容器镜像可公开推 Hub
- 部署文档覆盖三种场景：Docker Compose、纯 docker run、本地开发直接 `mvn spring-boot:run`

**Non-Goals:**
- 不做 CI / GitHub Actions（未来另立 change）
- 不做 Nexus / 制品仓（Docker Hub 就是唯一制品仓）
- 不做 Kubernetes / Helm chart
- 不改变 agent 部署模型（agent 仍走 `/opt/deploy-agent` 二进制安装，不容器化对外分发）
- 不改动 `AgentDownloadController.getInstallScript()` 里嵌的 Linux 安装脚本
- 不做灰度、蓝绿、版本回滚工具（回滚 = 手工 `docker run pengfei2022/redeploy-server:<旧版>`）
- 不做自动化测试门禁（本项目当前无测试，`mvn package` 用 `-DskipTests`）

## Decisions

### D1. 版本号唯一真源 = `server/pom.xml`

**决定**：`server/pom.xml` 的 `<version>` 是发布版本的唯一来源。所有 Docker tag、Git tag、部署文档引用都从它读。

**读取方式**：
```
mvn help:evaluate -Dexpression=project.version -q -DforceStdout
```

**Rationale**：Java 项目里 pom 是天然真源；同步机制越少越不会漂移。之前的痛点正是"pom 说 1.0.0，commit 说 0.1.1"这种双源不一致。

**Alternatives**：
- Git tag 作为真源 → 反直觉，需要在构建时把 tag 写进 jar manifest，多一层同步
- 独立 `VERSION` 文件 → 又多一个源，跟 pom 之间需要 sync 脚本

### D2. 版本归零：`1.0.0` → `0.1.0-SNAPSHOT`

**决定**：本次 change 落地时把 `pom.xml` 版本改成 `0.1.0-SNAPSHOT`，第一次跑 `release.ps1` 产出 `0.1.0` → Docker Hub `pengfei2022/redeploy-server:0.1.0`。

**Rationale**：项目实际处于 0.x 早期阶段，`1.0.0` 是脚手架残留。归零重起比"从 1.0.1 继续"更贴合真实状态。

### D3. SNAPSHOT 语义

**决定**：master 分支 HEAD 总是处于 `X.Y.Z-SNAPSHOT` 状态（"下一版开发中"）。`release` 脚本流程：
1. `0.1.0-SNAPSHOT` → `versions:set 0.1.0`（去 SNAPSHOT）
2. build + push + git tag `v0.1.0`
3. `versions:set 0.1.1-SNAPSHOT`（bump 到下一版 SNAPSHOT）
4. 再 commit 一次 "chore: bump to 0.1.1-SNAPSHOT"

**Rationale**：Maven 世界通用做法。tag 上是"已发布快照"，HEAD 上是"下一版开发中"，语义清晰。

**Trade-off**：每次 release 多一个 chore commit，历史稍嘈杂 —— 可接受。

### D4. 两步式脚本：build vs release

**决定**：
```
build.{ps1,sh}    [--version X.Y.Z] [--no-agent] [--platforms linux/amd64,linux/arm64]
    构建流水（不推、不改 git）：
    1. 计算 version：--version 优先；否则读 pom 去掉 -SNAPSHOT
    2. 交叉编译 agent（除非 --no-agent） → server/data/agents/deploy-agent-linux-{amd64,arm64}
    3. mvn -DskipTests clean package （--file server/pom.xml）
    4. docker buildx build --platform ... -t pengfei2022/redeploy-server:<ver>
                                          -t pengfei2022/redeploy-server:latest
                                          --load ./server
       (--load 只能加载单平台镜像到本地。多平台构建时脚本给出提示：
        本地验证请指定单 --platform，或跑 release 直接 --push)

release.{ps1,sh}  [patch|minor|major|X.Y.Z]
    发布流水（要推、要改 git）：
    1. 校验 git working tree clean（否则 abort）
    2. 读当前 pom（预期 X.Y.Z-SNAPSHOT），计算目标版本：
         patch：X.Y.Z → X.Y.Z （去掉 SNAPSHOT）
         minor：X.(Y+1).0
         major：(X+1).0.0
         显式 X.Y.Z：直接用
    3. mvn versions:set -DnewVersion=<目标> -DgenerateBackupPoms=false
    4. 调 build.sh/ps1 --version <目标>  但改为 buildx --push（多架构）
    5. git add pom.xml
       git commit -m "release: v<目标>"
       git tag v<目标>
    6. 计算下一版 SNAPSHOT（patch+1 + -SNAPSHOT）
       mvn versions:set -DnewVersion=<下一版>-SNAPSHOT
       git add pom.xml
       git commit -m "chore: bump to <下一版>-SNAPSHOT"
    7. git push --follow-tags
    8. echo "Released pengfei2022/redeploy-server:<目标>"
```

**Rationale**：build 用于本地反复调试（不污染 git、不推镜像），release 才是"决定发一版"的语义。

**Alternatives**：
- 单脚本 + `--push` 开关 → 语义模糊，杨明确要两步
- 只做 release，不给 build → 本地想验证镜像就得走 dry-run 分支，脚本会更复杂

### D5. Multi-arch 通过 `docker buildx`

**决定**：使用 `docker buildx build --platform linux/amd64,linux/arm64`。build 脚本默认 `--load` 单平台（本地验证）；release 脚本用 `--push` 直接推 Hub。

**Rationale**：一次动作产出两个 arch 的 manifest，Docker Hub 用户 pull 时自动匹配自己的 arch。

**前置要求**（写入 deployment.md）：
- Docker Desktop 自带 buildx
- Linux 需 `docker buildx create --use` 初始化 builder
- 需先 `docker login`（用户 `pengfei2022`）

### D6. Agent 交叉编译打入 server 镜像

**决定**：build 脚本在 mvn package **之前**跑 `GOOS=linux GOARCH=amd64` 和 `GOOS=linux GOARCH=arm64` 两次 `go build`，产物落到 `server/data/agents/deploy-agent-linux-{amd64,arm64}`。Dockerfile COPY 这个目录到镜像 `/app/data/agents/`。

**Rationale**：`AgentDownloadController` 就是从 `redeploy.agent-dir`（默认 `./data/agents/`）读取二进制对外分发。镜像里必须自带，否则用户从 UI 下 agent 立刻 404。

**Trade-off**：镜像里带 agent 二进制会稍大（每个 arch ~10MB），可接受。

**风险**：`server/data/agents/` 也被开发者本地 `mvn spring-boot:run` 使用，如果本地也有 agent 二进制，`git status` 会脏。→ 缓解：`.gitignore` 添加 `server/data/agents/deploy-agent-*`，只保留目录。

### D7. Dockerfile 结构

**决定**：多阶段构建。
```
FROM maven:3.9-eclipse-temurin-17 AS builder
  WORKDIR /build
  COPY server/pom.xml .
  RUN mvn -B dependency:go-offline
  COPY server/src ./src
  COPY server/data/agents /out/agents      # agent 二进制已在宿主机交叉编译好
  RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-alpine
  RUN apk add --no-cache tzdata bash curl \
      && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
      && echo "Asia/Shanghai" > /etc/timezone
  ENV TZ=Asia/Shanghai
  WORKDIR /app
  COPY --from=builder /build/target/redeploy-server-*.jar /app/redeploy-server.jar
  COPY --from=builder /out/agents /app/data/agents
  RUN mkdir -p /app/data/uploads /app/logs
  VOLUME ["/app/data", "/app/logs"]
  EXPOSE 9006
  HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://127.0.0.1:9006/actuator/health || curl -fsS http://127.0.0.1:9006/ || exit 1
  ENTRYPOINT ["java","-jar","/app/redeploy-server.jar"]
```

**Alternatives**：
- 让宿主机 `mvn package` 完再 COPY jar → 更快，但构建环境依赖宿主 JDK。取舍：脚本已在宿主 mvn package 一次；Dockerfile 内部再 mvn 是 fallback，保证脱离脚本也能 build。**改采纳**：Dockerfile 直接 COPY 宿主 jar，不在容器内 mvn，更快更简单。见下面 D7-final。

**D7-final**（简化后）：
```
FROM eclipse-temurin:17-jre-alpine
  RUN apk add --no-cache tzdata bash curl && ...时区
  WORKDIR /app
  ARG JAR_FILE=server/target/redeploy-server-*.jar
  COPY ${JAR_FILE} /app/redeploy-server.jar
  COPY server/data/agents/ /app/data/agents/
  RUN mkdir -p /app/data/uploads /app/logs
  VOLUME ["/app/data", "/app/logs"]
  EXPOSE 9006
  ENTRYPOINT ["java","-jar","/app/redeploy-server.jar"]
```
build 脚本负责先 `mvn package` 生成 jar，再 `docker buildx build` 从宿主 COPY。

**Rationale**：项目就一份代码，本地已经能 mvn；Dockerfile 里再放一层 mvn 会翻倍构建时间。

### D8. 敏感配置全部走环境变量

**决定**：`application.yml` 里所有敏感字段改为 Spring `${ENV:default}` 占位：
```yaml
redeploy:
  admin-token: ${REDEPLOY_ADMIN_TOKEN:changeme}
  ssh-encryption-key: ${REDEPLOY_SSH_ENCRYPTION_KEY:}
  dingtalk:
    enabled: ${REDEPLOY_DINGTALK_ENABLED:false}
    webhook-url: ${REDEPLOY_DINGTALK_WEBHOOK_URL:}
```
- `REDEPLOY_ADMIN_TOKEN` 默认 `changeme`（部署文档醒目告知必须改）
- `REDEPLOY_SSH_ENCRYPTION_KEY` 默认空字符串，触发原有"启动时自动生成"逻辑
- 其他非敏感字段（端口、路径、日志天数）保持字面量默认，仍允许 env 覆盖

**Rationale**：镜像可公开推 Hub，密钥不外泄；本地开发用默认值即可跑。

**Trade-off**：`changeme` 默认值容易被忘。→ 缓解：`ServerBootstrap` 检测到 `admin-token == "changeme"` 时启动日志打 WARN（可以放到后续 change，本次先只改 yml）。

### D9. docker-compose.yml 位置在仓库根

```yaml
services:
  redeploy-server:
    image: pengfei2022/redeploy-server:latest
    container_name: redeploy-server
    ports: ["9006:9006"]
    environment:
      TZ: Asia/Shanghai
      REDEPLOY_ADMIN_TOKEN: ${REDEPLOY_ADMIN_TOKEN:?必须设置}
      REDEPLOY_SSH_ENCRYPTION_KEY: ${REDEPLOY_SSH_ENCRYPTION_KEY:-}
      REDEPLOY_DINGTALK_ENABLED: ${REDEPLOY_DINGTALK_ENABLED:-false}
      REDEPLOY_DINGTALK_WEBHOOK_URL: ${REDEPLOY_DINGTALK_WEBHOOK_URL:-}
    volumes:
      - ./data:/app/data
      - ./logs:/app/logs
    restart: unless-stopped
```
配套 `.env.example`，把所有变量列全。

### D10. Git tag 与 push：release 脚本全自动

**决定**：`release` 脚本自动 `git commit + git tag + git push --follow-tags`。前置校验 `git status` 干净，失败时回滚提示（`git reset --hard HEAD~N`、`git tag -d v<X>`）。

**Rationale**：一键完成符合杨的实际使用节奏；出错时本地 tag 和 commit 都可回退。

### D11. 脚本平台策略

**决定**：双份维护（PowerShell + bash），逻辑一致，共用同一份"逻辑说明"注释。不引入 Node/Python 等第三种运行时。

**Trade-off**：双份维护有漂移风险。→ 缓解：脚本足够薄（每份 <200 行），改动同步。

## Risks / Trade-offs

- **[风险] Docker buildx 未初始化导致 release 中途失败** → release 脚本第一步 `docker buildx inspect` 探测，未就绪则给出 `docker buildx create --use` 提示后 abort
- **[风险] `docker login` 未登录导致 push 失败** → release 脚本先 `docker info` 检查登录状态，或直接尝试 `docker manifest inspect pengfei2022/redeploy-server` 探测；未登录时明确提示 `docker login -u pengfei2022`
- **[风险] Go 或 Maven 缺失** → build 脚本开头 `command -v go / mvn / docker` 依赖检查，缺失时早失败
- **[风险] `server/data/agents/` 存在旧文件污染** → build 前 `rm -f server/data/agents/deploy-agent-*`
- **[风险] Windows 下 `versions:set` 修改 pom 后行尾变 CRLF，git diff 噪声大** → `.gitattributes` 声明 `pom.xml text eol=lf`
- **[风险] release 到一半失败（比如 docker push 断网），HEAD 已经改了但没 tag** → 脚本用清晰的阶段日志 + 回滚命令提示；每个阶段独立 idempotent 尽量做到
- **[Trade-off] Dockerfile COPY 宿主 jar 意味着"必须先跑脚本才能 docker build"** → 可接受，脚本是唯一入口；给 Dockerfile 加注释说明前置条件
- **[Trade-off] 敏感配置默认值 `changeme` 可能被忘改** → deployment.md 顶部红字警告 + 后续可加启动 WARN 日志
- **[Trade-off] `docker buildx --load` 不支持多平台** → 本地验证时用 `--platforms linux/amd64` 单架构；deployment.md 说明

## Migration Plan

一次性：
1. 手工执行 `mvn -f server/pom.xml versions:set -DnewVersion=0.1.0-SNAPSHOT -DgenerateBackupPoms=false`
2. 首次 `./scripts/release.sh patch` 产出 `0.1.0`，验证：
   - Docker Hub 出现 `pengfei2022/redeploy-server:0.1.0` 和 `:latest`
   - 两个平台 manifest 都在（`docker buildx imagetools inspect pengfei2022/redeploy-server:0.1.0`）
   - Git 有 `v0.1.0` tag
   - pom 回到 `0.1.1-SNAPSHOT`

回滚：
- 本地：`git reset --hard <release 前 commit>` + `git tag -d v0.1.0` + `git push -d origin v0.1.0`
- Docker Hub：手工在 web 界面删除该 tag（或视为不可回滚，往前发新版覆盖 `:latest`）
