## 1. 工程化底座：测试与 CI

- [ ] 1.1 Server 测试框架搭建
  - [ ] 1.1.1 在 `server/pom.xml` 确认 JUnit 5、Spring Boot Test、H2 依赖已存在
  - [ ] 1.1.2 新增 Testcontainers 依赖（用于可选的 SQLite 集成测试）
  - [ ] 1.1.3 创建 `server/src/test/resources/application-test.yml` 测试配置
  - [ ] 1.1.4 为 `GroupMapper`、`ServerMapper`、`TaskMapper`、`DeployHistoryMapper` 写 CRUD 集成测试
  - [ ] 1.1.5 为 `AgentService` 写 mock 测试
  - [ ] 1.1.6 为 `DeployController` 写 `@WebMvcTest`

- [ ] 1.2 Agent 测试框架搭建
  - [ ] 1.2.1 在 `agent/internal/api` 中为 `task.go`、`upload.go`、`register.go` 写 `httptest` 测试
  - [ ] 1.2.2 为 `executor.go` 写 shell 执行与超时测试
  - [ ] 1.2.3 为 `config.go` 写加载与默认值测试

- [ ] 1.3 Frontend 测试框架搭建
  - [ ] 1.3.1 安装 Vitest + @vue/test-utils + msw
  - [ ] 1.3.2 为 `src/api/client.js` 写 mock 测试
  - [ ] 1.3.3 为 `Dashboard.vue` 写基础渲染测试

- [ ] 1.4 GitHub Actions CI
  - [ ] 1.4.1 创建 `.github/workflows/ci.yml`
  - [ ] 1.4.2 配置 server-test、agent-test、frontend-test、build 四个 job
  - [ ] 1.4.3 配置 main 分支合并后构建并推送 Docker 多架构镜像

- [ ] 1.5 代码质量工具
  - [ ] 1.5.1 在 `server/pom.xml` 引入 Spotless Maven Plugin
  - [ ] 1.5.2 在 `server/pom.xml` 引入 Checkstyle
  - [ ] 1.5.3 创建 `agent/.golangci.yml` 并引入基础 lint 规则
  - [ ] 1.5.4 在 `frontend/package.json` 配置 ESLint + Prettier 脚本
  - [ ] 1.5.5 跑一遍全量格式化并提交

## 2. 数据库迁移：Flyway

- [ ] 2.1 引入 Flyway
  - [ ] 2.1.1 在 `server/pom.xml` 添加 `org.flywaydb:flyway-core`
  - [ ] 2.1.2 在 `application.yml` 启用 Flyway 并配置 `baseline-on-migrate: true`
  - [ ] 2.1.3 移除 `spring.sql.init.mode` 和 `schema-locations` 配置

- [ ] 2.2 迁移脚本
  - [ ] 2.2.1 创建 `server/src/main/resources/db/migration/V1__baseline.sql`（与当前 schema 一致）
  - [ ] 2.2.2 创建 `V2__pipeline_model.sql`（新增 Pipeline / Stage / Step / Run / Parameter 表）
  - [ ] 2.2.3 创建 `V3__migrate_task_to_pipeline.sql`（迁移 Task → Pipeline，DeployHistory → Run）

- [ ] 2.3 验证
  - [ ] 2.3.1 空数据库启动验证
  - [ ] 2.3.2 有旧数据的数据库启动验证
  - [ ] 2.3.3 备份与回滚文档更新

## 3. Pipeline 模型与执行引擎

- [ ] 3.1 领域模型
  - [ ] 3.1.1 创建 `com.redeploy.pipeline.model.Pipeline`
  - [ ] 3.1.2 创建 `com.redeploy.pipeline.model.PipelineStage`
  - [ ] 3.1.3 创建 `com.redeploy.pipeline.model.PipelineStep`
  - [ ] 3.1.4 创建 `com.redeploy.pipeline.model.Run`
  - [ ] 3.1.5 创建 `com.redeploy.pipeline.model.RunStage`
  - [ ] 3.1.6 创建 `com.redeploy.pipeline.model.RunStep`
  - [ ] 3.1.7 创建对应 MyBatis mapper

- [ ] 3.2 状态机服务
  - [ ] 3.2.1 创建 `PipelineRunService`
  - [ ] 3.2.2 实现 `startRun`、`markStepSuccess`、`markStepFailed`、`cancelRun`
  - [ ] 3.2.3 实现非法状态转换的防御

- [ ] 3.3 执行器
  - [ ] 3.3.1 创建 `PipelineExecutor`，复用现有 `FileTransferService` 和 Agent HTTP 调用
  - [ ] 3.3.2 实现 Stage 串行/并行执行
  - [ ] 3.3.3 实现 Step 超时与重试
  - [ ] 3.3.4 实现 `shell`、`copy`、`healthcheck`、`approve` 四种 Step

- [ ] 3.4 兼容层
  - [ ] 3.4.1 修改 `DeployService` 内部使用 `PipelineRunService`
  - [ ] 3.4.2 修改 `TaskController` 从 Pipeline 表读取并适配旧 DTO
  - [ ] 3.4.3 保持旧 `/api/deploy` 行为不变

## 4. 参数系统

- [ ] 4.1 参数模型
  - [ ] 4.1.1 创建 `GlobalParameter` 和 `PipelineParameter` 实体与 mapper
  - [ ] 4.1.2 支持 `string`、`number`、`boolean`、`secret` 四种类型

- [ ] 4.2 模板引擎
  - [ ] 4.2.1 创建 `ParameterResolver`
  - [ ] 4.2.2 支持 `{{param}}`、`{{param:default}}`、`{{env.NAME}}`
  - [ ] 4.2.3 替换旧 `Task` 中的简单字符串替换逻辑

- [ ] 4.3 Secret 处理
  - [ ] 4.3.1 使用 AES 加密存储 secret 参数
  - [ ] 4.3.2 API 返回时脱敏为 `***`
  - [ ] 4.3.3 执行时解密，日志中不脱敏但禁止持久化明文

- [ ] 4.4 校验
  - [ ] 4.4.1 创建或更新参数时校验名称、类型、必填
  - [ ] 4.4.2 创建 Run 时校验运行时参数

## 5. API 版本化

- [ ] 5.1 v1 Controller
  - [ ] 5.1.1 创建 `com.redeploy.controller.v1.PipelineController`
  - [ ] 5.1.2 创建 `com.redeploy.controller.v1.RunController`
  - [ ] 5.1.3 创建 `com.redeploy.controller.v1.ParameterController`
  - [ ] 5.1.4 统一 v1 响应格式

- [ ] 5.2 旧 API 兼容
  - [ ] 5.2.1 保留现有 `DeployController`、`TaskController` 路由
  - [ ] 5.2.2 内部转发或适配到 v1 service
  - [ ] 5.2.3 更新 `docs/api/server-api.md`，旧接口标记 deprecated

- [ ] 5.3 OpenAPI 文档
  - [ ] 5.3.1 创建 `docs/api/openapi-v1.yaml`
  - [ ] 5.3.2 覆盖所有 v1 端点
  - [ ] 5.3.3 通过 Swagger Editor 或 CLI 校验

## 6. 前端适配

- [ ] 6.1 API client
  - [ ] 6.1.1 在 `frontend/src/api/client.js` 新增 v1 API 方法

- [ ] 6.2 页面
  - [ ] 6.2.1 新增 `Pipelines.vue` 页面
  - [ ] 6.2.2 新增 `PipelineEditor.vue` 页面
  - [ ] 6.2.3 新增 `Runs.vue` 页面
  - [ ] 6.2.4 新增 `RunDetail.vue` 页面
  - [ ] 6.2.5 更新路由 `frontend/src/router/index.js`

- [ ] 6.3 兼容
  - [ ] 6.3.1 原有 `/tasks` 和 `/history` 页面保留只读或重定向到 `/pipelines` 和 `/runs`

## 7. 文档更新

- [ ] 7.1 更新 `docs/api/server-api.md`
- [ ] 7.2 创建 `docs/api/openapi-v1.yaml`
- [ ] 7.3 更新 `README.md` 技术栈说明（Bootstrap 5 已过时，改为 Vue 3 + Element Plus）
- [ ] 7.4 更新 `AGENTS.md` 关于测试和 Flyway 的说明
- [ ] 7.5 更新 `docs/guide/deployment.md`，补充 Flyway 迁移与备份说明

## 8. 验证与归档

- [ ] 8.1 集成验证
  - [ ] 8.1.1 旧 `/api/deploy` 触发部署，结果与重构前一致
  - [ ] 8.1.2 新 `/api/v1/runs` 创建并执行包含 shell + copy + healthcheck 的 Pipeline
  - [ ] 8.1.3 旧 Task 和 DeployHistory 数据可在新页面查看
  - [ ] 8.1.4 CI 全绿

- [ ] 8.2 性能与兼容性验证
  - [ ] 8.2.1 空数据库启动时间 < 30 秒
  - [ ] 8.2.2 有 1000 条历史记录的 DB 迁移时间 < 60 秒

- [ ] 8.3 归档
  - [ ] 8.3.1 更新本 `tasks.md` 所有复选框状态
  - [ ] 8.3.2 执行 `openspec archive future-architecture-and-pipeline`
