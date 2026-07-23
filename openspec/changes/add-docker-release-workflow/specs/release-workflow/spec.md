## ADDED Requirements

### Requirement: 版本号唯一真源

系统 SHALL 使用 `server/pom.xml` 的 `<version>` 元素作为整个项目发布版本号的唯一来源。所有构建产物（jar 文件名、Docker 镜像 tag、Git tag）MUST 从该值派生，不允许在其他位置硬编码版本号。

#### Scenario: 从 pom 读取当前版本
- **WHEN** 构建脚本被调用
- **THEN** 脚本 MUST 通过 `mvn help:evaluate -Dexpression=project.version -q -DforceStdout` 读取版本号
- **AND** 该值 MUST 是后续 jar 命名、Docker tag、Git tag 的输入

#### Scenario: 不允许字面量版本
- **WHEN** 任何脚本、Dockerfile、docker-compose 文件出现具体版本字符串（如 `0.1.0`、`1.0.0`）
- **THEN** 该出现处 MUST 是从 pom 读取后动态注入，OR MUST 是 `latest` 通用 tag
- **AND** 例外：`docs/guide/deployment.md` 中可以出现示例版本号用于说明

### Requirement: 版本归零

系统 SHALL 在本次 change 落地时将 `server/pom.xml` 版本从 `1.0.0` 归零为 `0.1.0-SNAPSHOT`。

#### Scenario: 首次 release 产出 0.1.0
- **WHEN** 首次执行 `./scripts/release.{ps1,sh} patch`
- **THEN** Docker Hub 上 MUST 出现 `pengfei2022/redeploy-server:0.1.0`
- **AND** Git 仓库 MUST 出现 tag `v0.1.0`
- **AND** release 完成后 `pom.xml` 版本 MUST 变为 `0.1.1-SNAPSHOT`

### Requirement: SNAPSHOT 生命周期

master 分支 HEAD 上的 `pom.xml` 版本 MUST 始终为 `X.Y.Z-SNAPSHOT` 形式，表示"下一版开发中"。仅在 release 流程执行期间短暂脱离 SNAPSHOT。

#### Scenario: release 前后的版本状态
- **WHEN** release 脚本开始运行，当前 pom 为 `0.1.0-SNAPSHOT`
- **THEN** 脚本 MUST 依次经过 `0.1.0-SNAPSHOT` → `0.1.0`（构建 + 推送 + tag 完成）→ `0.1.1-SNAPSHOT`（再次 commit push）
- **AND** 结束状态 master HEAD 的 pom MUST 是 `0.1.1-SNAPSHOT`

#### Scenario: 非 SNAPSHOT 状态不允许长期存在
- **WHEN** 任何提交（除 release tag 所指的那次 commit）落到 master
- **THEN** 该提交对应的 pom 版本 MUST 以 `-SNAPSHOT` 结尾

### Requirement: build 脚本本地构建

系统 SHALL 提供 `scripts/build.ps1` 和 `scripts/build.sh` 两份等效脚本，用于在本地完成 agent 交叉编译、server 打包、Docker 镜像构建（不推送、不改 git）。

#### Scenario: 默认参数运行
- **WHEN** 执行 `./scripts/build.sh` 无参数
- **THEN** 脚本 MUST 读取 pom 版本并去除 `-SNAPSHOT` 后缀作为目标版本
- **AND** 交叉编译 agent 产出 `server/data/agents/deploy-agent-linux-amd64` 和 `server/data/agents/deploy-agent-linux-arm64`
- **AND** 执行 `mvn -f server/pom.xml -DskipTests clean package`
- **AND** 执行 `docker buildx build --platform linux/amd64 -t pengfei2022/redeploy-server:<版本> -t pengfei2022/redeploy-server:latest --load ./` 使用仓库根 Dockerfile 路径引用 server
- **AND** MUST NOT 执行 `docker push`
- **AND** MUST NOT 修改 git 状态

#### Scenario: 显式指定版本号
- **WHEN** 执行 `./scripts/build.sh --version 0.2.3`
- **THEN** 脚本 MUST 使用 `0.2.3` 作为镜像 tag
- **AND** MUST NOT 修改 pom.xml

#### Scenario: 跳过 agent 交叉编译
- **WHEN** 执行 `./scripts/build.sh --no-agent`
- **THEN** 脚本 MUST 跳过 `go build`
- **AND** 假定 `server/data/agents/` 下已存在所需二进制

#### Scenario: 依赖工具缺失
- **WHEN** 执行 build 脚本时 `go` / `mvn` / `docker` 任一命令不可用
- **THEN** 脚本 MUST 在开始工作前给出清晰错误信息并以非零退出码结束

### Requirement: release 脚本一键发布

系统 SHALL 提供 `scripts/release.ps1` 和 `scripts/release.sh` 两份等效脚本，用于将本地代码发布为新版本：bump pom → 构建 → 推送 Docker Hub → 提交 tag → 回滚到下一版 SNAPSHOT。

#### Scenario: patch 版本发布
- **WHEN** 执行 `./scripts/release.sh patch`，当前 pom 为 `0.1.0-SNAPSHOT`
- **THEN** 脚本 MUST 依次：
  1. 校验 git working tree clean，否则 abort
  2. 校验 `docker buildx` 可用
  3. 校验已 `docker login`（有登录态）
  4. 执行 `mvn -f server/pom.xml versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false`
  5. 交叉编译 agent + `mvn package` + `docker buildx build --platform linux/amd64,linux/arm64 -t pengfei2022/redeploy-server:0.1.0 -t pengfei2022/redeploy-server:latest --push`
  6. `git add server/pom.xml && git commit -m "release: v0.1.0"`
  7. `git tag v0.1.0`
  8. `mvn -f server/pom.xml versions:set -DnewVersion=0.1.1-SNAPSHOT -DgenerateBackupPoms=false`
  9. `git add server/pom.xml && git commit -m "chore: bump to 0.1.1-SNAPSHOT"`
  10. `git push --follow-tags`
- **AND** 最终 Docker Hub MUST 有 `0.1.0` 和 `latest` 两个 tag（multi-arch manifest 包含 linux/amd64 + linux/arm64）
- **AND** 最终 pom MUST 为 `0.1.1-SNAPSHOT`

#### Scenario: minor 版本发布
- **WHEN** 执行 `./scripts/release.sh minor`，当前 pom 为 `0.1.5-SNAPSHOT`
- **THEN** 发布版本 MUST 为 `0.2.0`
- **AND** 结束状态 pom MUST 为 `0.2.1-SNAPSHOT`

#### Scenario: major 版本发布
- **WHEN** 执行 `./scripts/release.sh major`，当前 pom 为 `0.2.3-SNAPSHOT`
- **THEN** 发布版本 MUST 为 `1.0.0`
- **AND** 结束状态 pom MUST 为 `1.0.1-SNAPSHOT`

#### Scenario: 显式版本发布
- **WHEN** 执行 `./scripts/release.sh 2.5.0`
- **THEN** 发布版本 MUST 为 `2.5.0`
- **AND** 结束状态 pom MUST 为 `2.5.1-SNAPSHOT`

#### Scenario: 工作区不干净时拒绝
- **WHEN** 执行 release 脚本时 `git status --porcelain` 输出非空
- **THEN** 脚本 MUST 立即 abort 并提示"请先提交或 stash 变更"
- **AND** MUST NOT 修改 pom
- **AND** MUST NOT 调用 docker / git 命令

#### Scenario: 未登录 Docker Hub 时拒绝
- **WHEN** 执行 release 脚本时未 `docker login` 或凭据已失效
- **THEN** 脚本 MUST 在 push 之前的校验阶段 abort
- **AND** MUST 提示 `docker login -u pengfei2022`

### Requirement: Docker 镜像多架构

系统 SHALL 将发布到 Docker Hub 的 `pengfei2022/redeploy-server` 镜像构建为 multi-arch manifest，至少包含 `linux/amd64` 和 `linux/arm64` 两个平台。

#### Scenario: 校验多架构 manifest
- **WHEN** release 完成后执行 `docker buildx imagetools inspect pengfei2022/redeploy-server:<版本>`
- **THEN** 输出 MUST 同时包含 `linux/amd64` 和 `linux/arm64` 条目

### Requirement: server Dockerfile

系统 SHALL 在仓库根提供 `Dockerfile`（用于 server 镜像构建），基于 `eclipse-temurin:17-jre-alpine` 运行时。

#### Scenario: 镜像内容
- **WHEN** 镜像构建完成
- **THEN** 镜像 MUST 包含：
  - `/app/redeploy-server.jar`（Spring Boot fat jar）
  - `/app/data/agents/deploy-agent-linux-amd64`
  - `/app/data/agents/deploy-agent-linux-arm64`
- **AND** MUST 声明 `EXPOSE 9006`
- **AND** MUST 声明 volumes：`/app/data`、`/app/logs`
- **AND** MUST 设置时区 `Asia/Shanghai`
- **AND** ENTRYPOINT MUST 为 `java -jar /app/redeploy-server.jar`

#### Scenario: agent 二进制作为前置条件
- **WHEN** `docker build` 执行时 `server/data/agents/deploy-agent-linux-amd64` 或 `deploy-agent-linux-arm64` 不存在
- **THEN** 构建 MUST 失败并给出清晰错误信息

### Requirement: docker-compose 单机部署

系统 SHALL 在仓库根提供 `docker-compose.yml`，允许用户以 `docker compose up -d` 一键启动 server。

#### Scenario: 一键启动
- **WHEN** 用户在包含 `docker-compose.yml` 和 `.env`（含 `REDEPLOY_ADMIN_TOKEN`）的目录执行 `docker compose up -d`
- **THEN** MUST 拉取 `pengfei2022/redeploy-server:latest` 并启动容器
- **AND** MUST 映射 9006 端口到宿主
- **AND** MUST 挂载 `./data` → `/app/data` 和 `./logs` → `/app/logs`
- **AND** MUST 通过环境变量注入 `REDEPLOY_ADMIN_TOKEN` 等敏感配置

#### Scenario: 必填环境变量缺失时失败
- **WHEN** 用户未在 `.env` 或 shell 环境中提供 `REDEPLOY_ADMIN_TOKEN`
- **THEN** `docker compose up` MUST 失败并提示该变量必须设置

### Requirement: 敏感配置环境变量化

`server/src/main/resources/application.yml` 中所有敏感字段 MUST 使用 Spring `${ENV:default}` 占位符，允许运行时通过环境变量覆盖。

#### Scenario: admin-token 可覆盖
- **WHEN** 容器以 `REDEPLOY_ADMIN_TOKEN=abc123` 启动
- **THEN** 运行时 `redeploy.admin-token` MUST 等于 `abc123`

#### Scenario: 未设置时使用默认值
- **WHEN** 未设置任何 `REDEPLOY_*` 环境变量
- **THEN** 应用 MUST 使用 `application.yml` 中的默认值启动（`admin-token` 默认为占位符 `changeme`，`ssh-encryption-key` 默认为空字符串以触发自动生成）

#### Scenario: 覆盖字段清单
- **THEN** 至少以下配置项 MUST 支持环境变量覆盖：
  - `REDEPLOY_ADMIN_TOKEN` → `redeploy.admin-token`
  - `REDEPLOY_SSH_ENCRYPTION_KEY` → `redeploy.ssh-encryption-key`
  - `REDEPLOY_DINGTALK_ENABLED` → `redeploy.dingtalk.enabled`
  - `REDEPLOY_DINGTALK_WEBHOOK_URL` → `redeploy.dingtalk.webhook-url`
  - `REDEPLOY_DINGTALK_NOTIFY_MODE` → `redeploy.dingtalk.notify-mode`

### Requirement: 部署文档

系统 SHALL 提供 `docs/guide/deployment.md`，覆盖发版流程、Docker 部署、本地非 Docker 启动三种场景。

#### Scenario: 文档章节完整
- **THEN** `docs/guide/deployment.md` MUST 包含以下章节：
  - 版本号规则（pom 是唯一真源、SNAPSHOT 语义）
  - 发版流程（`build.{ps1,sh}` / `release.{ps1,sh}` 参数、前置要求 docker login / buildx）
  - Docker Compose 部署（`.env` 示例、启动、停止、日志、数据卷位置）
  - `docker run` 单容器部署（完整命令示例）
  - 本地开发不用 Docker（`mvn spring-boot:run`、需要提前手工放 agent 二进制到 `server/data/agents/`、如何设置环境变量）
  - 环境变量清单（每个变量：名称、含义、默认值、是否必填）
  - 常见故障排查（Docker Hub 未登录、buildx 未初始化、agent 二进制缺失导致下载 404）

### Requirement: Git tag 与 push 自动化

`release` 脚本 MUST 在流程末尾自动执行 `git commit`、`git tag v<版本>`、`git push --follow-tags`，无需用户手工操作。

#### Scenario: tag 与 remote 同步
- **WHEN** release 脚本正常结束
- **THEN** 本地 MUST 有 tag `v<版本>` 指向 release commit
- **AND** 远端 origin MUST 已收到该 tag 和相关 commits
