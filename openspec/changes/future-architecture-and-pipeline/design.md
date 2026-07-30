## Context

Re-Deploy 的当前版本（0.1.x）是一个为"Jenkins 无法直连客户服务器"场景设计的部署中间件。它用一个 Spring Boot Server 做编排、一个 Go Agent 做执行、一个 Vue 前端做管理，已经能完成基本闭环。但项目在快速迭代中积累了以下技术债：

1. **零测试**：所有验证依赖手工点击和实际部署，重构风险不可控。
2. **数据库 schema 管理原始**：靠 `spring.sql.init.mode: always` 每次启动跑 `CREATE TABLE IF NOT EXISTS`，无法安全地做列变更、加索引、迁移数据。
3. **核心模型太薄**：`Task` 直接对应一个部署动作，`DeployHistory` 只记录整体成功失败。没有 Stage/Step 抽象，无法表达"先停服 → 分发文件 → 启动 → 健康检查"这种多步编排，也无法支持条件执行、重试、审批。
4. **参数系统几乎不存在**：只有简单的字符串替换，没有类型、作用域、默认值、密文保护。
5. **API 没有版本**：所有接口混在 `/api/*`，后续任何改动都可能破坏 Jenkins 调用方。

杨希望在不推倒重来的前提下，先把项目升级到"可稳定演进"的状态，并为后续企业级特性（RBAC、审计、高可用、K8s 插件等）打好基础。

## Goals / Non-Goals

**Goals:**

- 建立 Server / Agent / Frontend 三层测试体系，PR 有自动化门禁。
- 用 Flyway 替代 schema 启动重建，支持安全迁移。
- 引入 Pipeline / Stage / Step / Run 领域模型，替代现有 `Task` + `DeployHistory` 的扁平结构。
- 实现 Run 状态机与步骤级执行控制（超时、重试、条件、忽略失败）。
- 建立多作用域参数系统，支持 string / number / boolean / secret 类型。
- 新增 `/api/v1/*` 版本化 API，旧 `/api/*` 保留兼容层。
- 提供 OpenAPI 3.0 文档。
- 保持现有部署能力和 Agent 协议不变。

**Non-Goals:**

- 不改数据库选型（继续用 SQLite，但 Flyway 抽象后可平滑迁移到 PostgreSQL/MySQL）。
- 不改通信协议（Server ↔ Agent 继续用 HTTP，不引入 gRPC / 长连接）。
- 不做高可用改造（不引入 Redis / 队列 / Server 多实例）。
- 不做 RBAC 与审计（这是阶段 3-4 的内容）。
- 不做 K8s / Docker / Terraform 执行器（保持 `shell` 和 `copy`，为插件系统留扩展点）。
- 不做前端 UI 大改版（只新增 Pipeline 管理页面，保持 Element Plus 风格）。

## Decisions

### D1. 测试策略：从集成测试开始，而不是先补单元测试

**决定**：优先为 Service / Mapper / Repository 层写集成测试，使用 Spring Boot Test + H2（或 Testcontainers SQLite）；Agent 层用 `httptest` 做 handler 集成测试；Frontend 优先覆盖 API client 和纯函数工具。

**Rationale**：当前代码大量依赖 Spring 注入和 MyBatis，先写单元测试需要大量 mock，收益低。集成测试能快速建立回归安全网。

**Trade-off**：测试运行速度比纯单元测试慢，CI 时间会增加 3-5 分钟。可接受。

### D2. Flyway 作为 schema 迁移工具

**决定**：引入 `org.flywaydb:flyway-core`，迁移脚本放在 `server/src/main/resources/db/migration/V{version}__{name}.sql`。首次启动时执行 baseline，把当前 schema 标记为 V1。

**Rationale**：团队熟悉 SQL，Flyway 对 Spring Boot 集成最好，不需要额外运维。

**Alternatives**：
- Liquibase：更强大但 XML/YAML 学习成本高。
- 手写 migration runner：重复造轮子。

### D3. 新 Pipeline 模型与旧 Task 模型共存，逐步迁移

**决定**：
- 新增 `pipeline` 包，包含 `Pipeline`、`PipelineStage`、`PipelineStep`、`Run`、`RunStage`、`RunStep` 等实体。
- 旧 `Task` 表保留，通过 migration 脚本把 `Task` 数据导入到 `Pipeline`。
- 旧 `/api/tasks/*` 和 `/api/deploy` 保留，内部调用新的 PipelineService，必要时做模型适配。
- Web UI 新增 `/pipelines`、`/runs` 页面，原有 `/tasks`、`/history` 页面可保留为只读或重定向。

**Rationale**：避免大爆炸式重构，降低回滚风险。旧接口兼容让 Jenkins 调用方不感知变化。

**Trade-off**：运行时会同时存在两套表和两套 API，代码里需要适配层。计划在 0.3.x 彻底下线旧 API。

### D4. Run 状态机采用显式状态字段 + 事件方法

**决定**：每个 `Run` / `RunStage` / `RunStep` 都有 `status` 字段，状态转换通过 service 层显式方法控制（如 `startRun()`、`markStepSuccess()`、`markStepFailed()`），不在业务代码里直接 `setStatus()`。

**状态定义**：
```
pending → queued → running → success
                    ↓
                  failed
                    ↓
                cancelled
```

**Rationale**：显式状态机避免非法转换，方便后续加审计、钩子、通知。

### D5. Step 类型一期只保留 4 种

**决定**：
- `shell`：在 Agent 上执行 shell 命令（复用现有 executor）。
- `copy`：把制品从 Server 上传到 Agent 指定目录（复用现有 FileTransferService）。
- `healthcheck`：HTTP 探测目标地址。
- `approve`：人工审批，暂停 Run 等待 UI 确认。

**Rationale**：覆盖当前 90% 的使用场景，同时证明 Step 抽象可扩展。后续通过插件机制增加 `kubernetes`、`terraform` 等类型。

### D6. 参数系统保持 `{{param}}` 语法，扩展默认值与环境变量

**决定**：
- 保持向后兼容：`{{version}}` 继续生效。
- 新增：`{{param:default}}` 默认值。
- 新增：`{{env.NAME}}` 读取 Server 环境变量（需显式开启，避免泄漏）。
- secret 类型参数在存储时加密，日志中显示为 `***`。

**Rationale**：用户已习惯现有语法，渐进扩展比换语法成本低。

### D7. API 版本化：新增 `/api/v1/*`，旧 `/api/*` 做兼容层

**决定**：
- 新 controller 放在 `com.redeploy.controller.v1`。
- 旧 controller 保留，内部可调用 v1 service，或做 DTO 转换。
- 旧 API 在文档中标记为 deprecated，计划在 0.3.x 移除。

**Rationale**：给外部调用方明确的迁移窗口。

### D8. 代码质量工具选型

**决定**：
- Java：Spotless Maven Plugin（统一格式化）+ Checkstyle（风格检查）。
- Go：`golangci-lint`（标准配置）。
- Frontend：ESLint + Prettier（Element Plus 官方风格）。

**Rationale**：工具链成熟，与 GitHub Actions 集成简单。

## Risks / Trade-offs

- **[风险] Flyway baseline 后，已有 SQLite 数据库可能没有 `flyway_schema_history` 表** → 通过 `baselineOnMigrate = true` 自动处理。
- **[风险] Pipeline 模型与旧 Task 数据迁移不完全匹配** → migration 脚本采用"最佳 effort"：旧 `Task.stepsDefinition` JSON 直接映射为单个 Stage 下的多个 Step；`DeployHistory` 迁移为 `Run` 但缺少 Stage/Step 明细。
- **[风险] 测试引入 Docker 依赖后，Windows 开发者体验下降** → Testcontainers 需要 Docker Desktop；文档中明确说明，并提供 H2 轻量测试 profile 作为 fallback。
- **[风险] 参数 secret 加密增加复杂度** → 一期先用 AES 加密（复用现有 `SshEncryptionUtils`），后续迁移到 Vault。
- **[Trade-off] 兼容层让代码短期内更复杂** → 接受，这是平滑迁移的必要成本。
- **[Trade-off] 状态机显式方法增加 boilerplate** → 接受，换取正确性和可维护性。

## Migration Plan

1. **准备阶段**
   - 备份现有 `server/data/redeploy.db`。
   - 在 feature branch 完成所有代码改动。

2. **数据库迁移**
   - 添加 Flyway 依赖和配置。
   - 创建 `V1__baseline.sql`（与当前 schema 一致）。
   - 创建 `V2__pipeline_model.sql`（新增 Pipeline / Run 相关表）。
   - 创建 `V3__migrate_task_to_pipeline.sql`（把 Task / DeployHistory 数据导入新表）。

3. **应用启动验证**
   - 空数据库启动：Flyway 自动创建全部表。
   - 有旧数据启动：Flyway baseline + V2/V3 迁移后，新旧数据共存。

4. **回滚**
   - 代码回滚：切回旧分支。
   - 数据回滚：从备份恢复 `redeploy.db`。
   - 注意：一旦新 Pipeline 被使用并产生新 Run，回滚会导致数据不一致，需提前告知。

## Success Criteria

- [ ] `mvn test` 在 CI 中稳定通过，覆盖率 > 30%。
- [ ] `go test ./...` 在 CI 中稳定通过。
- [ ] `npm run test`（Vitest）在 CI 中稳定通过。
- [ ] 用旧 `/api/deploy` 触发一次部署，结果与重构前一致。
- [ ] 用新 `/api/v1/runs` 创建并执行一个 Pipeline，包含 shell + copy + healthcheck 三步。
- [ ] 旧 `Task` 和 `DeployHistory` 数据可在新 `/pipelines` 和 `/runs` 页面查看。
- [ ] OpenAPI 文档可在 `docs/api/openapi-v1.yaml` 中读取并通过校验。
