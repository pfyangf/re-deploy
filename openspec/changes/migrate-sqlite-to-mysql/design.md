## Context

当前 re-deploy server 使用 SQLite 作为嵌入式数据库，数据文件位于 `./data/redeploy.db`。项目已通过功能验证阶段，准备交付团队正式使用。团队运维要求统一使用 MySQL 8 作为数据库后端，便于备份、监控和多实例管理。

现有 SQLite 依赖分布在：
- Maven 依赖（`sqlite-jdbc`）
- 数据源配置（`application.yml`）
- Schema DDL（`schema.sql`，6 张表使用 `AUTOINCREMENT` 等 SQLite 语法）
- 6 个 MyBatis Mapper（18 处 `datetime('now')` 和 `last_insert_rowid()`）
- `DataMigration.java`（`PRAGMA table_info` 列检查）
- 三层 schema 演进机制（schema.sql + migration SQL + Java 兜底）

## Goals / Non-Goals

**Goals:**
- 将数据库后端从 SQLite 切换到 MySQL 8
- 保持三层 schema 演进机制在 MySQL 下正常工作
- 提供一次性数据迁移脚本，支持停机迁移
- 连接信息支持环境变量注入，Docker 部署友好
- MySQL 不可用时 server 启动失败（不做降级）

**Non-Goals:**
- 不引入 Flyway/Liquibase 等重型迁移框架
- 不支持运行时双写或渐进式迁移
- 不支持 SQLite 和 MySQL 双模式共存
- 不改变任何业务逻辑或 API 接口

## Decisions

### D1: MySQL Connector/J 8.x 替换 sqlite-jdbc

**选择**: `mysql-connector-j` 8.x（MySQL 官方 JDBC 驱动）
**替代方案**: MariaDB Connector/J
**理由**: MySQL 官方驱动，与 MySQL 8 兼容性最佳，团队熟悉度高。

### D2: 连接信息通过环境变量 + 默认值

**选择**: `application.yml` 使用 `${MYSQL_HOST:localhost}` 占位符，Docker 启动时通过环境变量覆盖。
**替代方案**: 纯硬编码 / Spring Profile 多环境配置
**理由**: 单文件配置 + 环境变量覆盖最简洁，Docker 部署友好，不需要维护多份配置文件。

### D3: useGeneratedKeys 替换 @SelectKey

**选择**: 所有 Mapper 的 INSERT 方法改用 `@Options(useGeneratedKeys = true, keyProperty = "id")`
**替代方案**: `@SelectKey` + `LAST_INSERT_ID()`
**理由**: `useGeneratedKeys` 是 MyBatis 原生机制，不依赖数据库特定函数，代码更简洁。

### D4: NOW() 替换 datetime('now')

**选择**: 所有 Mapper 中的 `datetime('now')` 替换为 `NOW()`
**理由**: MySQL 标准时间函数，语义等价。

### D5: INFORMATION_SCHEMA.COLUMNS 替换 PRAGMA table_info

**选择**: `DataMigration.ensureColumnExists` 改用 `SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?`
**理由**: MySQL 标准元数据查询方式，功能等价。

### D6: 数据迁移用纯 SQL 导出脚本

**选择**: 提供 shell 脚本，使用 `sqlite3` 导出 SQL，sed 清洗语法差异后导入 MySQL
**替代方案**: Java 一次性迁移工具（双数据源 SELECT → INSERT）
**理由**: 数据量小（几百到几千条），shell 脚本更轻量，用完即弃。

### D7: Schema DDL 适配

| SQLite | MySQL |
|--------|-------|
| `INTEGER PRIMARY KEY AUTOINCREMENT` | `BIGINT AUTO_INCREMENT PRIMARY KEY` |
| `BOOLEAN DEFAULT 0` | `TINYINT(1) DEFAULT 0` |
| `TEXT` | `TEXT`（兼容）/ `LONGTEXT`（detail_logs 大字段） |
| `DEFAULT CURRENT_TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP`（兼容） |

## Risks / Trade-offs

- **[风险] 迁移窗口停机** → 数据量小，预计迁移时间 < 5 分钟；选择低峰期执行
- **[风险] MySQL 不可用导致 server 无法启动** → 符合预期设计；确保 MySQL 先于 server 启动（Docker Compose depends_on）
- **[风险] 三层 schema 演进机制在 MySQL 下行为差异** → `continue-on-error` 在 MySQL 重复 ALTER 时报 "Duplicate column name"，仍被吞掉；需验证
- **[取舍] 不再支持嵌入式单文件部署** → 换取团队统一运维标准，可接受
