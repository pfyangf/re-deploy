## ADDED Requirements

### Requirement: Flyway 集成

系统 SHALL 引入 Flyway 作为数据库迁移工具，替代 `spring.sql.init.mode: always` + `schema.sql` 的启动重建机制。

#### Scenario: 依赖引入
- **WHEN** 查看 `server/pom.xml`
- **THEN** MUST 包含 `org.flywaydb:flyway-core` 依赖

#### Scenario: 配置启用
- **WHEN** 查看 `server/src/main/resources/application.yml`
- **THEN** MUST 启用 Flyway：`spring.flyway.enabled: true`
- **AND** MUST 配置 baseline：`spring.flyway.baseline-on-migrate: true`
- **AND** MUST 配置 baseline 版本：`spring.flyway.baseline-version: 1`

#### Scenario: 迁移脚本位置
- **WHEN** 查看 `server/src/main/resources/db/migration/`
- **THEN** MUST 存在按 `V{version}__{description}.sql` 命名的 SQL 脚本

### Requirement: Baseline 脚本

系统 SHALL 提供 `V1__baseline.sql`，与当前生产 schema 完全一致。

#### Scenario: 新环境首次启动
- **WHEN** 在空数据库上启动 server
- **THEN** Flyway MUST 自动执行 V1 及后续迁移脚本
- **AND** 应用 MUST 正常启动

#### Scenario: 已有数据环境启动
- **WHEN** 在已有 `redeploy.db` 的数据库上启动 server
- **THEN** Flyway MUST 通过 `baseline-on-migrate` 将现有 schema 标记为 V1
- **AND** MUST NOT 删除或修改已有数据

### Requirement: Pipeline 模型迁移脚本

系统 SHALL 提供 `V2__pipeline_model.sql`，新增 Pipeline / Stage / Step / Run 相关表。

#### Scenario: 新增表结构
- **WHEN** V2 执行后
- **THEN** 数据库 MUST 存在以下表：
  - `pipelines`
  - `pipeline_stages`
  - `pipeline_steps`
  - `runs`
  - `run_stages`
  - `run_steps`
  - `pipeline_parameters`

#### Scenario: 表字段规范
- **THEN** 所有主键 MUST 为 `INTEGER PRIMARY KEY AUTOINCREMENT`
- **AND** 外键 MUST 有 `FOREIGN KEY` 约束
- **AND** 时间字段 MUST 使用 `TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

### Requirement: 数据迁移脚本

系统 SHALL 提供 `V3__migrate_task_to_pipeline.sql`，将现有 `Task` 和 `DeployHistory` 数据导入新模型。

#### Scenario: Task 迁移为 Pipeline
- **WHEN** V3 执行时
- **THEN** 每条 `tasks` 记录 MUST 生成一条 `pipelines` 记录
- **AND** `tasks.steps_definition` JSON MUST 解析为 `pipeline_steps` 并归到一个默认 `pipeline_stages` 下
- **AND** `pipelines.source_task_id` MUST 保存原 Task ID 以便追溯

#### Scenario: DeployHistory 迁移为 Run
- **WHEN** V3 执行时
- **THEN** 每条 `deploy_history` 记录 MUST 生成一条 `runs` 记录
- **AND** `runs.status` MUST 映射为 `pending / running / success / failed`
- **AND** `runs.server_ids` MUST 保留原值

### Requirement: 移除 schema.sql 启动重建

系统 SHALL 修改 `server/src/main/resources/schema.sql` 与 `application.yml`，不再每次启动重建 schema。

#### Scenario: 配置变更
- **WHEN** 查看 `application.yml`
- **THEN** `spring.sql.init.mode` MUST 设置为 `never` 或被移除
- **AND** `spring.sql.init.schema-locations` MUST 被移除

#### Scenario: schema.sql 内容
- **THEN** `schema.sql` 可保留作为文档或归档，但 MUST 不被 Spring Boot 自动执行

### Requirement: 迁移可回滚与备份提示

系统 SHALL 在文档中明确告知用户升级前备份数据库。

#### Scenario: 部署文档
- **THEN** `docs/guide/deployment.md` MUST 包含"升级前备份 `./data/redeploy.db`"的醒目提示
- **AND** MUST 说明 Flyway 一旦执行后不可自动回滚到旧 schema

## MODIFIED Requirements

- 现有 `schema.sql` 执行机制由 Spring Boot init 改为 Flyway 管理。

## REMOVED Requirements

无。
