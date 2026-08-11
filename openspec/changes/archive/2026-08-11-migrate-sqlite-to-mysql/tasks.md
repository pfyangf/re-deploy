## 1. 依赖与配置

- [x] 1.1 修改 `server/pom.xml`：移除 `org.xerial:sqlite-jdbc` 依赖，新增 `com.mysql:mysql-connector-j` 8.x
- [x] 1.2 修改 `server/src/main/resources/application.yml`：数据源 URL 改为 `jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DB:redeploy}?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true`
- [x] 1.3 修改 `application.yml`：driver-class-name 改为 `com.mysql.cj.jdbc.Driver`，新增 `username: ${MYSQL_USERNAME:redeploy}` 和 `password: ${MYSQL_PASSWORD:redeploy}`

验证：`mvn compile` 通过；启动时连不上 MySQL 报错清晰

## 2. Schema DDL 适配

- [x] 2.1 修改 `server/src/main/resources/schema.sql`：6 张表的 `INTEGER PRIMARY KEY AUTOINCREMENT` -> `BIGINT AUTO_INCREMENT PRIMARY KEY`
- [x] 2.2 修改 `schema.sql`：`BOOLEAN DEFAULT 0` -> `TINYINT(1) DEFAULT 0`（tasks 表 enabled 列）
- [x] 2.3 修改 `schema.sql`：`deploy_history.detail_logs` 列类型改为 `LONGTEXT`（大字段），其余 `TEXT` 保持
- [x] 2.4 检查 `migration/V001__add_detail_logs.sql`：`ALTER TABLE deploy_history ADD COLUMN detail_logs TEXT;` 在 MySQL 下语法兼容，已改为 `LONGTEXT` 与 schema.sql 一致

验证：空 MySQL 库启动 server，6 张表全部创建成功，列类型正确

## 3. Mapper SQL 适配（6 个 Mapper，18 处改动）

- [x] 3.1 `GroupMapper.java`：`datetime('now')` -> `NOW()`（INSERT + UPDATE 共 2 处）；`@SelectKey(last_insert_rowid())` -> `@Options(useGeneratedKeys=true, keyProperty="id")`
- [x] 3.2 `ServerMapper.java`：同上模式（datetime 2 处 + SelectKey 1 处）
- [x] 3.3 `TaskMapper.java`：同上模式（datetime 2 处 + SelectKey 1 处）
- [x] 3.4 `DeployHistoryMapper.java`：同上模式（datetime 1 处 + SelectKey 1 处）
- [x] 3.5 `ArtifactMapper.java`：同上模式（datetime 1 处 + SelectKey 1 处）
- [x] 3.6 `AgentMapper.java`：同上模式（datetime 1 处 + SelectKey 1 处）

验证：`mvn compile` 通过；手工调各 Mapper 的 insert/update，时间戳写入正确，自增 ID 回填正确

## 4. DataMigration.java 适配

- [x] 4.1 修改 `ensureColumnExists` 方法：`PRAGMA table_info(tableName)` -> `SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?`（使用 `SELECT DATABASE()` 获取当前库名）
- [x] 4.2 调整 `ensureColumnExists` 的参数传入：使用 `jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)` 动态获取数据库名，无需硬编码

验证：删除某列后启动 server，`ensureColumnExists` 检测到缺失并 ALTER 补列；列已存在时跳过

## 5. 数据迁移脚本

- [x] 5.1 新建 `scripts/migrate-sqlite-to-mysql.sh`：使用 `sqlite3 redeploy.db .dump` 导出 SQL，grep 只保留 INSERT，sed 清洗表名引号
- [x] 5.2 脚本接受参数：SQLite db 文件路径、MySQL 连接信息（host/port/db/user/password）
- [x] 5.3 脚本执行流程：导出 -> 清洗 -> 去掉 schema.sql 已覆盖的 CREATE TABLE 语句（只保留 INSERT） -> 导入 MySQL -> 验证行数一致

验证：在测试环境用真实 SQLite 数据库跑脚本，对比迁移前后行数一致

## 6. 文档更新

- [x] 6.1 更新 `AGENTS.md`：Data & Schema 段，将 SQLite 相关描述改为 MySQL（数据源、驱动、三层机制中 PRAGMA -> INFORMATION_SCHEMA 的变更）
- [x] 6.2 更新 `AGENTS.md`：Build / Run 段，补充 MySQL 部署前置条件与环境变量说明
- [x] 6.3 更新 `docs/guide/deployment.md`：新增 MySQL 安装与建库说明、环境变量配置表、数据迁移流程

## 7. 端到端验证

- [x] 7.1 全量构建：`mvn package` 通过
- [x] 7.2 空 MySQL 库启动 server，schema.sql 建表成功，三层 schema 演进机制正常工作
- [x] 7.3 执行数据迁移脚本，将老 SQLite 数据导入 MySQL，行数与原始数据一致
- [x] 7.4 用迁移后的 MySQL 数据启动 server，前端各功能（服务器列表、任务列表、部署历史详情）正常展示
- [x] 7.5 验证 MySQL 不可用时 server 启动失败（断开 MySQL 后启动，确认报错清晰）
