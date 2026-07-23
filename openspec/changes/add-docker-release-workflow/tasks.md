## 1. 版本归零与配置环境变量化

- [x] 1.1 修改 `server/pom.xml`：`<version>` 从 `1.0.0` 改为 `0.1.0-SNAPSHOT`
- [x] 1.2 修改 `server/src/main/resources/application.yml`：将 `redeploy.admin-token`、`redeploy.ssh-encryption-key`、`redeploy.dingtalk.enabled`、`redeploy.dingtalk.webhook-url`、`redeploy.dingtalk.notify-mode` 五个字段改为 `${ENV_VAR:default}` 占位形式
- [x] 1.3 新增 `.gitattributes` 声明 `*.xml text eol=lf`、`*.yml text eol=lf`、`*.sh text eol=lf`，避免跨平台行尾符差异
- [x] 1.4 更新 `.gitignore`：排除 `server/data/agents/deploy-agent-*` 二进制、`.env`、`data/`（根目录 compose 挂载点）、`logs/`

## 2. server Dockerfile

- [x] 2.1 在仓库根创建 `Dockerfile`（server 镜像），基于 `eclipse-temurin:17-jre-alpine`
- [x] 2.2 Dockerfile 内 `COPY server/target/redeploy-server-*.jar /app/redeploy-server.jar`
- [x] 2.3 Dockerfile 内 `COPY server/data/agents/deploy-agent-linux-amd64 /app/data/agents/`
- [x] 2.4 Dockerfile 内 `COPY server/data/agents/deploy-agent-linux-arm64 /app/data/agents/`
- [x] 2.5 Dockerfile 内安装 `tzdata bash curl`、设置 `TZ=Asia/Shanghai`、软链 `/etc/localtime`
- [x] 2.6 Dockerfile 声明 `EXPOSE 9006`、`VOLUME ["/app/data","/app/logs"]`、`ENTRYPOINT ["java","-jar","/app/redeploy-server.jar"]`
- [x] 2.7 Dockerfile 添加 `HEALTHCHECK` 探测 9006 端口
- [x] 2.8 新增 `.dockerignore`：排除 `**/*.md`、`.git`、`.idea`、`target/`（子层）、`openspec/`、`docs/`、`scripts/`（除必要）、`.archon` `.agents` `.claude` `.opencode`、`agent/`（agent 源码不需要进 server 镜像）、`server/data/redeploy.db*`、`server/logs/`、`data/`、`logs/`

## 3. docker-compose 与环境变量样例

- [x] 3.1 在仓库根创建 `docker-compose.yml`，服务名 `redeploy-server`，使用 `pengfei2022/redeploy-server:latest`，映射 9006 端口，挂载 `./data`、`./logs`，`restart: unless-stopped`
- [x] 3.2 compose 内声明必填变量 `REDEPLOY_ADMIN_TOKEN`（用 `${REDEPLOY_ADMIN_TOKEN:?必须设置}` 语法），可选变量默认空
- [x] 3.3 新增 `.env.example`，列出所有 `REDEPLOY_*` 变量、注释含义、默认值示例

## 4. build 脚本（本地构建，双平台）

- [x] 4.1 创建 `scripts/build.sh`：参数解析 `--version`、`--no-agent`、`--platforms`、`--push`
- [x] 4.2 `build.sh` 开头依赖检查 `command -v mvn go docker`，缺失则退出
- [x] 4.3 `build.sh` 版本决定逻辑：`--version` 优先 > 读 pom 去掉 `-SNAPSHOT`
- [x] 4.4 `build.sh` 清理 `server/data/agents/deploy-agent-*` 后交叉编译 amd64 + arm64 agent
- [x] 4.5 `build.sh` 执行 `mvn -f server/pom.xml -DskipTests clean package`
- [x] 4.6 `build.sh` 执行 `docker buildx build`：默认 `--platform linux/amd64 --load`，`--push` 时改多平台 `--push`
- [x] 4.7 创建 `scripts/build.ps1`：与 `build.sh` 参数、行为完全对齐
- [x] 4.8 两份脚本顶部添加"逻辑说明"注释，标注需要同步修改
- [x] 4.9 脚本给出 buildx 未就绪时的清晰提示（`docker buildx create --use redeploy-builder`）

## 5. release 脚本（发布，双平台）

- [x] 5.1 创建 `scripts/release.sh`：参数 `patch|minor|major|<X.Y.Z>`
- [x] 5.2 `release.sh` 前置校验：`git status --porcelain` 空、`docker buildx` 可用、`docker login` 状态可用（`docker info | grep Username` 或 `docker manifest inspect` 试探）
- [x] 5.3 `release.sh` 读 pom 当前版本，计算目标版本
- [x] 5.4 `release.sh` 执行 `mvn versions:set` 设为目标版本（去 SNAPSHOT）
- [x] 5.5 `release.sh` 调用 `build.sh --version <目标> --platforms linux/amd64,linux/arm64 --push`
- [x] 5.6 `release.sh` 提交 `release: v<目标>`、打 tag `v<目标>`
- [x] 5.7 `release.sh` 执行 `mvn versions:set` 设为 `<下一版>-SNAPSHOT`，提交 `chore: bump to <下一版>-SNAPSHOT`
- [x] 5.8 `release.sh` 执行 `git push --follow-tags`
- [x] 5.9 `release.sh` 结束后打印 Docker Hub 拉取命令与验证命令（`docker buildx imagetools inspect`）
- [x] 5.10 创建 `scripts/release.ps1`：与 `release.sh` 完全对齐
- [x] 5.11 每个阶段失败时打印"回滚命令建议"（如 `git reset --hard HEAD~N`、`git tag -d v<X>`、`docker buildx rm ...`）

## 6. 部署文档

- [x] 6.1 创建 `docs/guide/deployment.md`
- [x] 6.2 文档章节：版本号规则（pom 是唯一真源、SNAPSHOT 语义、bump 规则）
- [x] 6.3 文档章节：发版流程（build vs release、前置要求 `docker login -u pengfei2022`、`docker buildx create --use`）
- [x] 6.4 文档章节：Docker Compose 部署（`.env` 示例、`docker compose up -d`、日志查看、数据卷位置、升级方式）
- [x] 6.5 文档章节：`docker run` 单容器部署完整命令
- [x] 6.6 文档章节：本地开发不用 Docker（`mvn spring-boot:run`、手工放 agent 到 `server/data/agents/`、如何设置环境变量：Windows PowerShell + Linux bash 两种示例）
- [x] 6.7 文档章节：环境变量清单表格（名称 / 含义 / 默认值 / 是否必填）
- [x] 6.8 文档章节：常见故障排查（Docker Hub 未登录、buildx 未初始化、agent 二进制缺失导致 UI 下载 404、`REDEPLOY_ADMIN_TOKEN` 未改导致安全告警）
- [x] 6.9 在 `README.md` "快速开始"处增加指向 `docs/guide/deployment.md` 的入口

## 7. 首次发版验证

- [ ] 7.1 手工在 Docker Hub 创建仓库 `pengfei2022/redeploy-server`（如未自动创建）
- [ ] 7.2 本地 `docker login -u pengfei2022`
- [ ] 7.3 本地 `docker buildx create --use --name redeploy-builder`（如未创建）
- [ ] 7.4 执行 `./scripts/build.sh`（或 ps1）验证本地镜像可跑：`docker run --rm -p 9006:9006 -e REDEPLOY_ADMIN_TOKEN=test pengfei2022/redeploy-server:0.1.0`，访问 http://localhost:9006 成功
- [ ] 7.5 执行 `./scripts/release.sh patch`，验证：
  - Docker Hub 出现 `pengfei2022/redeploy-server:0.1.0` 和 `:latest`
  - `docker buildx imagetools inspect pengfei2022/redeploy-server:0.1.0` 显示 amd64 + arm64
  - Git tag `v0.1.0` 已推到远端
  - master 上 pom 为 `0.1.1-SNAPSHOT`
- [ ] 7.6 拷贝 `.env.example` 为 `.env`，填 `REDEPLOY_ADMIN_TOKEN`，执行 `docker compose up -d` 验证一键起
- [ ] 7.7 从 UI 下载 agent 二进制，验证 `deploy-agent-linux-amd64` 和 `deploy-agent-linux-arm64` 均可下载（不报 404）

## 8. 清理与最终提交

- [ ] 8.1 移除仓库中的临时 build 产物（本地 build 用的 `server/target/`、`server/data/agents/deploy-agent-*`）
- [x] 8.2 检查 `.gitignore` 已正确排除
- [x] 8.3 更新 `AGENTS.md` 相关章节：Build/Run 部分补充脚本入口、指向 deployment.md
- [ ] 8.4 归档本 change：`openspec archive add-docker-release-workflow`
