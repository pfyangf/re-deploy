## Why

功能验证阶段完成，团队正式使用需要 MySQL 8 作为数据库后端。SQLite 的单文件部署模式不满足团队多实例、可备份、可监控的运维需求。切换到 MySQL 独立部署，连不上即启动失败。

## What Changes

- **BREAKING**: 数据库从 SQLite 切换到 MySQL 8，需要独立部署 MySQL 实例
- 替换 Maven 依赖：`sqlite-jdbc` → `mysql-connector-j`
- 重写数据源配置：JDBC URL、driver、用户名/密码
- 改写 schema.sql：`AUTOINCREMENT` → `AUTO_INCREMENT`，时间类型适配
- 改写 6 个 MyBatis Mapper 中的 SQLite 特有 SQL（18 处）：`datetime('now')` → `NOW()`，`last_insert_rowid()` → `useGeneratedKeys`
- 改写 DataMigration.java：`PRAGMA table_info` → `INFORMATION_SCHEMA.COLUMNS`
- 提供一次性数据迁移脚本：SQLite → MySQL 数据导出导入
- 连接信息支持环境变量注入（Docker 部署友好）

## Capabilities

### New Capabilities
- `mysql-database`: MySQL 数据库后端——数据源配置、连接管理、schema DDL 适配、数据迁移脚本

### Modified Capabilities
- `deploy-history`: 三层 schema 演进机制适配 MySQL（PRAGMA table_info → INFORMATION_SCHEMA.COLUMNS，migration SQL 语法校验）

## Impact

- **依赖**: `pom.xml` 替换 JDBC 驱动
- **配置**: `application.yml` 数据源配置全面替换，新增 username/password
- **Schema**: `schema.sql` 6 张表 DDL 重写，`migration/V001__add_detail_logs.sql` 语法检查
- **Mapper**: 6 个 Mapper 文件共 18 处 SQLite 特有 SQL 需替换
- **Java**: `DataMigration.java` 的列存在性检查逻辑重写
- **部署**: 需要独立 MySQL 8 实例，Docker Compose 或手动部署
- **数据**: 一次性停机迁移，提供导出/导入脚本
- **DataDirInitializer**: `./data/` 目录仍需保留（uploads/agents），逻辑不变
