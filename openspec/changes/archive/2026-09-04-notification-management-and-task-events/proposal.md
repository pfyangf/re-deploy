## Why

当前 `alert-notification` 能力只支持单一钉钉 webhook 通道，且配置硬编码在 `application.yml` 里——一条 webhook、一个失败触发点。实际运维中：

- 不同事件（部署失败、服务器离线、agent 升级失败）想发到不同群组/人；
- 部分客户环境没有钉钉，需要企微、邮件或自建 webhook；
- 临时调试时想静默某个群组的告警，但代码改起来麻烦；
- 任务执行过程中除了「成功/失败」，运维还想看到「开始」「超时」「步骤失败」等中间状态。

需要一个独立的「通知管理」子系统：通知通道可插拔、通知目标可配置、触发事件可按条选择。

## What Changes

- **BREAKING**：将 `alert-notification` 中嵌入 `application.yml` 的 `redeploy.dingtalk.webhook-url` / `redeploy.dingtalk.secret` 等硬编码字段移除；改为运行时从 `notification_config` 表读取。
- **BREAKING**：`alert-notification` 的 `redeploy.notification.mode`（`failure-only` / `all`）字段移除——触发事件粒度改为在每条 `notification_config` 上独立配置，废弃全局开关。
- 新增 `notification-config` 表（MySQL schema 演进机制三层同步）和 `notification_config` Mapper。
- 新增四种通道实现：钉钉（带签名）、企业微信机器人、自定义 webhook、邮件（SMTP）。每种实现 `NotificationChannel` 接口。
- 新增通知配置 CRUD API（`/api/notifications`）和发送历史 API（`/api/notifications/history`）。
- 在 `task-execution` 子系统接入事件总线：任务开始、任务成功、任务失败、步骤失败、步骤超时、agent 离线、agent 上线 7 类事件。
- 每条 `notification_config` 选择订阅哪些事件 + 绑定到哪些服务器组/任务模板。
- 通知发送统一走 `NotificationDispatcher`，重试、退避、日志沿用现有 `AlertService` 行为并下沉到 dispatcher 内。

## Capabilities

### New Capabilities

- `notification-management`：通知通道抽象、通知配置 CRUD、运行时配置管理、发送历史
- `task-execution-notification`：任务执行事件定义、事件分发、订阅匹配、通知触发

### Modified Capabilities

- `alert-notification`：拆掉硬编码 webhook 配置；保留「失败重试 3 次指数退避」「所有发送结果写日志」两条语义，重命名为 `notification-management` 下的通道实现
- `task-execution`：扩展事件类型（任务级 + 步骤级），新增事件订阅匹配逻辑

## Impact

- **服务端**：新增 `NotificationChannel` 接口 + 4 个实现；新增 `NotificationConfig` / `NotificationHistory` 实体；`AlertService` 改为 `NotificationDispatcher`；`DeployService` / `TaskService` 在状态变更处发事件；`application.yml` 移除钉钉相关字段
- **数据库**：`schema.sql` 加 `notification_config` / `notification_history` 两张表；`migration/V002__add_notification_tables.sql` 增量 DDL；`DataMigration.ensureColumnExists` 兜底不需要
- **API**：新增 `/api/notifications`、`/api/notifications/{id}/test`、`/api/notifications/history`；`/api/deploy` 触发后端行为变化（事件流代替同步 alert 调用）
- **配置迁移**：旧 `application.yml` 中的钉钉 webhook 必须导出成一条 `notification_config` 才能继续生效；提供「从 yml 导入」的一次性脚本
- **前端**：通知管理页面（列表、新建、编辑、测试发送、历史）；部署页面无改动
- **依赖**：邮件通道需引入 `spring-boot-starter-mail`
- **回归**：现有「部署失败钉钉告警」行为不能丢——拆通道后默认 `failure-only` 行为由一条预置的 `notification_config`（订阅 `task.failed`）覆盖