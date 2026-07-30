## Why

Re-Deploy 目前是一个能完成"Jenkins 触发 → Server 分发 → Agent 执行"基本闭环的轻量部署工具，但工程化程度和核心抽象都停留在 0.1.x 内测阶段。随着使用场景增多，当前架构会在可靠性、可维护性、可扩展性上快速遇到瓶颈：

1. **没有测试与 CI**：`server/src/test/` 为空，`go test ./...` 是 no-op，任何重构都依赖手工回归，风险极高。
2. **schema 管理脆弱**：`schema.sql` 在每次启动时通过 `spring.sql.init.mode: always` 重新执行，依赖 `CREATE TABLE IF NOT EXISTS`，无法做列变更、索引管理、数据迁移。
3. **部署模型单一**：所有逻辑都塞在 `DeployService` 里，"任务"直接对应一次部署；没有流水线（Pipeline）、阶段（Stage）、步骤（Step）的抽象，无法表达复杂编排（如蓝绿、健康检查、人工审批、回滚）。
4. **参数系统薄弱**：仅支持简单的 `{{param}}` 字符串替换，没有参数作用域、类型校验、默认值、密文处理。
5. **API 未版本化**：所有接口都是 `/api/*`，后续重构很难保证外部调用方（Jenkins、CLI）兼容。

本次 change 的目标是在**不破坏现有部署能力**的前提下，先把工程化底座补齐，并把核心执行模型从"单次部署"升级为"可编排的流水线执行"，为后续高可用、可观测、安全等企业特性打下基础。

## What Changes

### 工程化底座

- 在 `server/` 引入 JUnit 5 集成测试体系，使用 H2 内存数据库 + Testcontainers 覆盖 MyBatis mapper 与 service 层。
- 在 `agent/` 引入 Go 标准测试 + `httptest`，覆盖 HTTP handlers、executor、config。
- 在 `frontend/` 引入 Vitest，覆盖 API client 和核心组件逻辑。
- 新增 GitHub Actions workflow：PR 阶段跑 `mvn test`、`go test ./...`、`npm run build`；合并到 main 后构建多架构 Docker 镜像。
- 引入代码质量工具：Spotless（Java）、Checkstyle、golangci-lint、ESLint/Prettier。
- 用 Flyway 替代 `schema.sql` 的每次启动重建，建立可迁移的数据库版本管理。

### 核心模型重构：Pipeline

- 新增领域模型：
  - `Pipeline`：流水线定义，包含多个 Stage。
  - `Stage`：阶段，可配置串行或并行执行，包含多个 Step。
  - `Step`：最小执行单元，类型包括 `shell`、`copy`、`healthcheck`、`approve`（人工审批）、`jenkins-download`。
  - `Run`：一次流水线执行实例，维护状态机与步骤级结果。
- 将现有 `Task` 与 `DeployHistory` 映射到新的 `Pipeline` / `Run` 模型，保持旧 API 兼容。
- 用状态机驱动 Run 生命周期：`pending → queued → running → success / failed / cancelled`。
- 支持步骤级配置：超时、重试次数、失败时忽略、执行条件（condition）。

### 参数系统

- 引入三层参数作用域：
  - 全局参数（Global）
  - 流水线参数（Pipeline-level）
  - 运行时参数（Run-level，如版本号、构建号）
- 支持参数类型：`string`、`number`、`boolean`、`secret`。
- secret 类型在日志中脱敏显示。
- 模板替换语法保持 `{{param}}`，扩展为支持默认值 `{{param:default}}` 和表达式 `{{env.NAME}}`。

### API 版本化

- 新增 `/api/v1/*` 路由，新功能只暴露在 v1。
- 现有 `/api/*` 路由保留作为兼容层，内部转发或复用 v1 实现。
- 提供 OpenAPI 3.0 文档（`docs/api/openapi-v1.yaml`）。

## Capabilities

### New Capabilities

- `testing-ci`：Server / Agent / Frontend 三层测试体系 + GitHub Actions CI + 代码质量门禁。
- `database-migration`：基于 Flyway 的数据库版本管理，替代 `schema.sql` 启动重建。
- `pipeline-model`：流水线编排模型（Pipeline / Stage / Step / Run）及状态机。
- `parameter-system`：多作用域、多类型参数系统，支持 secret 脱敏。
- `api-versioning`：REST API v1 版本化与 OpenAPI 文档。

### Modified Capabilities

- `task-execution`：现有任务执行能力被 Pipeline 模型吸收，原 `Task` 表数据迁移到新模型，旧 API 保留兼容层。
- `deploy-history`：历史记录从单次部署记录扩展为 Run 记录，包含 Stage/Step 级明细。

## Impact

- **代码**：
  - 新增 `server/src/test/` 下的大量测试代码。
  - 新增 `.github/workflows/ci.yml`。
  - 新增 `server/src/main/resources/db/migration/`（Flyway 脚本）。
  - 新增 `server/src/main/java/com/redeploy/pipeline/` 包及模型、service、controller。
  - 修改 `server/src/main/resources/schema.sql`：移除启动重建逻辑，改为 Flyway baseline。
  - 修改 `server/src/main/resources/application.yml`：增加 Flyway 配置、测试 profile。
  - 新增 `docs/api/openapi-v1.yaml`。
- **数据**：
  - 现有 SQLite 数据需通过 Flyway baseline + migration 脚本迁移到新 schema。
  - 旧 `Task` 记录会被迁移为 `Pipeline`；旧 `DeployHistory` 迁移为 `Run`。
- **工具链**：
  - 开发者需要安装 Docker（Testcontainers 依赖）。
  - CI 依赖 GitHub Actions 与 Docker Hub 凭据。
- **兼容性**：
  - 旧 `/api/deploy`、`/api/tasks/*` 等接口继续可用，内部调用 v1 实现或做适配。
  - Web UI 优先使用 v1 API。
- **不影响**：
  - Agent 的 HTTP API 和命令执行语义（Agent 对 Server 暴露的接口不变）。
  - 现有 Docker 构建与发版脚本（`scripts/build.*`、`scripts/release.*`）。
  - 现有的 SSH 堡垒机终端能力。
