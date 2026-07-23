## Why

当前项目发版没有可复现的流程：`server/pom.xml` 版本号硬编码在 `1.0.0`，commit message 里却出现过 `0.1.0`、`0.1.1`，两者已经对不上；server 没有 Dockerfile，无法产出容器镜像；没有任何构建脚本，`README` / `AGENTS.md` 让开发者手工 `mvn package` + 手工交叉编译 agent 放到 `server/data/agents/`，容易漏 arch、漏版本对齐。需要把"发版"从口口相传的手工动作固化为一条可重复、版本一致的脚本。

## What Changes

- 新增 `server/Dockerfile`，多阶段构建 Spring Boot server 镜像，运行时基镜像 `eclipse-temurin:17-jre-alpine`
- 新增 `.dockerignore`
- 新增 `docker-compose.yml`，单机一键起 server，挂 `data / logs` 卷，通过环境变量注入敏感配置
- 新增 `scripts/build.ps1` 和 `scripts/build.sh`：本地构建（bump pom 版本 → 交叉编译 agent linux/amd64+arm64 → `mvn package` → `docker buildx build` 多架构镜像本地加载），**不推送**
- 新增 `scripts/release.ps1` 和 `scripts/release.sh`：发版流程（校验 git clean → 决定目标版本 → 调 build 脚本 → `docker push` 两个 tag → git commit + tag + push → 将 pom 回滚到下一版 `-SNAPSHOT`）
- 新增 `docs/guide/deployment.md`：讲清楚发版流程、Docker 部署步骤、环境变量清单、本地不用 Docker 时的启动方式
- **BREAKING**（内部）：`server/pom.xml` 版本号从 `1.0.0` 归零为 `0.1.0-SNAPSHOT`，首次 release 产出 `0.1.0`。任何依赖 `1.0.0` 字面量的地方需要同步（当前搜索无外部依赖）
- 修改 `server/src/main/resources/application.yml`：所有敏感配置项（`redeploy.admin-token`、`redeploy.ssh-encryption-key`、`redeploy.dingtalk.webhook-url` 等）改为支持 `${ENV_VAR:default}` 语法，允许运行时环境变量覆盖
- 修改 `scripts/uninstall.sh` 无需变动（属于 agent 卸载，与本次发版流程无关）；不新增 install.sh（server 安装通过 docker-compose，agent 安装脚本仍由 `AgentDownloadController.getInstallScript()` 返回）

## Capabilities

### New Capabilities
- `release-workflow`: 版本号管理（Maven 版本作为唯一真源）+ 构建脚本（build / release 两步式）+ Docker 镜像构建与推送 + git tag 与版本同步的一整套发版流程

### Modified Capabilities
<!-- 无现有 spec 的 requirement 变更；application.yml 的环境变量覆盖属于配置调整，不改变任何已存 capability 的 requirement -->

## Impact

- **代码**：
  - 新增 `server/Dockerfile`、`.dockerignore`、`docker-compose.yml`
  - 新增 `scripts/build.{ps1,sh}`、`scripts/release.{ps1,sh}`
  - 修改 `server/pom.xml`（版本号归零 + 可能新增 `versions-maven-plugin` 用于 `versions:set`）
  - 修改 `server/src/main/resources/application.yml`（敏感字段改为环境变量占位）
  - 新增 `docs/guide/deployment.md`
- **发布物**：Docker Hub `pengfei2022/redeploy-server:<version>` 和 `:latest`，multi-arch（linux/amd64 + linux/arm64）
- **依赖 / 工具链**：需要 Docker Desktop 或 Linux 下 `docker buildx`；需要 Go 1.21+（交叉编译 agent）；需要 Maven 3.8+；需要登录过 `docker login`（用户 `pengfei2022`）
- **不影响**：agent 独立部署流程；Jenkins 集成；现有数据库 schema；agent Dockerfile 保持不动
