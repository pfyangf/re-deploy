## 1. 数据库与迁移

- [x] 1.1 `schema.sql` 新增 `notification_config` 表（id, channel_type, channel_config JSON, name, enabled, event_types JSON, server_group_ids JSON nullable, task_template_ids JSON nullable, created_at, updated_at）
- [x] 1.2 `schema.sql` 新增 `notification_history` 表（id, config_id, event_type, server_id, task_id, deploy_history_id, channel_type, target JSON, payload JSON, status, error_message, sent_at, idx 三列）
- [x] 1.3 新建 `migration/V002__add_notification_tables.sql`（与 schema.sql 同 DDL，IF NOT EXISTS 包裹供老库兜底）
- [x] 1.4 验证三层 schema 演进机制：新建库走 schema.sql、老库走 migration SQL

## 2. 实体与 Mapper

- [x] 2.1 新建 `NotificationConfig.java` 实体 + `NotificationConfigMapper.java`（annotation-based，CRUD + 按事件类型查 enabled）
- [x] 2.2 新建 `NotificationHistory.java` 实体 + `NotificationHistoryMapper.java`（insert + 分页查询 + 按时间清理）
- [x] 2.3 新建 `SystemEvent.java` POJO（事件模型，ApplicationEventPublisher 用）

## 3. 通道接口与四通道实现

- [x] 3.1 定义 `NotificationChannel` 接口（`type()` + `send()`）
- [x] 3.2 实现 `DingTalkChannel`（含 HMAC-SHA256 签名，`channel_config` 含 `webhookUrl` + `secret` 可选）
- [x] 3.3 实现 `WechatWorkChannel`（机器人 webhook，`channel_config` 含 `webhookUrl`）
- [x] 3.4 实现 `WebhookChannel`（通用 POST JSON，`channel_config` 含 `url` + 可选 `headers`）
- [x] 3.5 实现 `EmailChannel`（SMTP 发送纯文本，`channel_config` 含 from/to/cc/subjectPrefix）
- [x] 3.6 `pom.xml` 新增 `spring-boot-starter-mail` 依赖

## 4. 通知调度核心

- [x] 4.1 实现 `NotificationDispatcher`：`@Component`，暴露 `dispatch(SystemEvent)` 方法 + `@EventListener` 订阅
- [x] 4.2 dispatcher 内部：匹配 enabled config → 校验 eventType + server_group_ids + task_template_ids → 按 channel_type 路由
- [x] 4.3 dispatcher 内置 3 次指数退避重试（沿用 AlertService 行为）
- [x] 4.4 每次发送落 `notification_history`（status + error_message）

## 5. 历史清理

- [x] 5.1 `NotificationHistoryCleanupTask`：`@Scheduled` 每天凌晨 3:30 清理 30 天前历史（`@Scheduled(cron = "0 30 3 * * ?")`，保留天数可配）

## 6. 服务端改造（事件化）

- [x] 6.1 `DeployService`：触发部署开始 → `applicationEventPublisher.publishEvent(new SystemEvent("TASK_STARTED", ...))`
- [x] 6.2 `DeployService`：终态成功/失败 → 对应 `TASK_SUCCESS` / `TASK_FAILED` 事件
- [ ] 6.3 `TaskExecutor` / agent 轮询回调：步骤失败/超时 → `STEP_FAILED` / `STEP_TIMEOUT` 事件（**后续迭代**：当前 agent 上报 step 失败未在服务端单独抛事件，会先随 TASK_FAILED 一起通知；如需更细粒度，下一轮接入）
- [ ] 6.4 `AgentHeartbeatService`：agent offline/online → `AGENT_OFFLINE` / `AGENT_ONLINE` 事件（**后续迭代**：当前心跳服务未提供，需要先确认心跳在哪个 service）
- [x] 6.5 移除 `DeployService` 内对 `AlertService` 的直接调用，改为事件发布

## 7. 配置迁移与兼容

- [ ] 7.1 移除 `application.yml` 中 `redeploy.dingtalk.*` 字段（**保留 deprecated 注释**，因为 `NotificationConfigMigrator` 启动期仍需读取迁移；计划下个版本移除）
- [x] 7.2 配置迁移：用 `NotificationConfigMigrator`（`CommandLineRunner`）替代 shell 脚本——服务启动时自动读 yml → 插入等价 `notification_config`；DB 已有钉钉配置则跳过不覆盖
- [x] 7.3 `AlertService` 标记 `@Deprecated`（保留为空壳方法，注释说明迁移到 dispatcher）

## 8. API 与前端

- [x] 8.1 实现 `NotificationController`：`/api/notification/configs` 全套 CRUD + `/api/notification/configs/{id}/test` + `/api/notification/history`
- [x] 8.2 `POST /api/notification/configs/{id}/test`：走通道 `send()` 不走事件分发，立即返回 success/fail
- [x] 8.3 前端：通知管理页面 `Notifications.vue`（列表 + 新建/编辑表单 + 测试按钮 + 历史 tab + 分页）
- [x] 8.4 前端：通知配置表单按 `channelType` 提供 JSON 模板（钉钉/企微/webhook/邮件各有 schema），切换时一键填入示例

## 9. 文档

- [x] 9.1 `docs/guide/notification.md`：通知管理使用说明（CRUD + 事件订阅 + 迁移步骤 + 故障排查 + API 速查）
- [ ] 9.2 `docs/api/server-api.md`：补 `/api/notification/*` 全套接口文档（**留到下一轮与 remote-file-download 一起补**）

## 10. 测试与验证

- [ ] 10.1 单元测试：`NotificationDispatcher` 匹配逻辑（事件维度 + 服务器组维度 + 任务模板维度）
- [ ] 10.2 单元测试：四通道 `send()` 方法（mock HTTP / SMTP）
- [ ] 10.3 集成测试：从 `DeployService` 触发失败 → 通知 history 表出现成功记录（mock 通道）
- [ ] 10.4 配置迁移脚本验证：老 yml 配置 → 新库有一条等价 notification_config

> 备注：10.x 测试任务暂留不勾，原因——项目根 `AGENTS.md` 注明 `server/src/test/` 当前为空、openspec 参考 re-deploy-tool 也是把 13 测试节点保留待办；本次变更以「可工作」为目标落地，测试待后续单独 PR 补齐。

## 实际交付文件清单

**新增（Java）**
- `server/src/main/java/com/redeploy/notification/NotificationChannel.java`
- `server/src/main/java/com/redeploy/notification/NotificationChannelRegistry.java`
- `server/src/main/java/com/redeploy/notification/NotificationMessage.java`
- `server/src/main/java/com/redeploy/notification/NotificationResult.java`
- `server/src/main/java/com/redeploy/notification/NotificationJson.java`
- `server/src/main/java/com/redeploy/notification/NotificationRenderer.java`
- `server/src/main/java/com/redeploy/notification/NotificationDispatcher.java`
- `server/src/main/java/com/redeploy/notification/NotificationAsyncConfig.java`
- `server/src/main/java/com/redeploy/notification/NotificationConfigMigrator.java`
- `server/src/main/java/com/redeploy/notification/NotificationHistoryCleanupTask.java`
- `server/src/main/java/com/redeploy/notification/channel/DingTalkChannel.java`
- `server/src/main/java/com/redeploy/notification/channel/WechatWorkChannel.java`
- `server/src/main/java/com/redeploy/notification/channel/WebhookChannel.java`
- `server/src/main/java/com/redeploy/notification/channel/EmailChannel.java`
- `server/src/main/java/com/redeploy/model/NotificationConfig.java`
- `server/src/main/java/com/redeploy/model/NotificationHistory.java`
- `server/src/main/java/com/redeploy/model/SystemEvent.java`
- `server/src/main/java/com/redeploy/repository/NotificationConfigMapper.java`
- `server/src/main/java/com/redeploy/repository/NotificationHistoryMapper.java`
- `server/src/main/java/com/redeploy/controller/NotificationController.java`

**修改**
- `server/pom.xml` — 加 `spring-boot-starter-mail`
- `server/src/main/resources/application.yml` — 加 `spring.mail.*` + 通知管理保留期 + dingtalk deprecated 注释
- `server/src/main/resources/schema.sql` — 追加 `notification_config` / `notification_history` 表
- `server/src/main/resources/migration/V002__add_notification_tables.sql` — 新建
- `server/src/main/java/com/redeploy/service/DeployService.java` — 用 ApplicationEventPublisher 替换 AlertService
- `server/src/main/java/com/redeploy/service/AlertService.java` — 标 @Deprecated，保留空壳

**新增（前端）**
- `frontend/src/views/Notifications.vue` — 通知管理页面

**修改（前端）**
- `frontend/src/api/client.js` — 加 `/api/notification/*` 8 个方法
- `frontend/src/router/index.js` — 加 `/notifications` 路由
- `frontend/src/components/AppSidebar.vue` — 加「通知管理」菜单

**新增（文档）**
- `docs/guide/notification.md` — 使用指南

**新增（OpenSpec）**
- `openspec/changes/notification-management-and-task-events/{proposal,design,tasks}.md` + `specs/*`
- `openspec/specs/notification-management/spec.md`（apply 后）
- `openspec/specs/task-execution-notification/spec.md`（apply 后）
