# 通知管理使用指南

> 对应变更：`notification-management-and-task-events`

通知管理让 ReDeploy 在部署/任务执行的关键节点通过钉钉/企业微信/Webhook/邮件把消息推出去。所有通道配置走数据库（`notification_config` / `notification_history`），运行时可改，不用重启服务。

## 1. 总览

| 能力 | 通道 | 备注 |
| --- | --- | --- |
| 多通道 | 钉钉（带签名）、企业微信、自定义 Webhook、邮件 SMTP | 一个通知配置只能选一种通道 |
| 事件订阅 | 8 种内置事件 | 同一条配置可订阅多个事件 |
| 限定范围 | 服务器组 + 任务模板 | 两个维度都可独立限定，留空表示不限 |
| 失败重试 | 3 次指数退避 | 与原 `AlertService` 行为一致 |
| 发送历史 | 全量记录 + 30 天自动清理 | `notification_history` 表 |
| 启动迁移 | yml 老配置自动入库 | `NotificationConfigMigrator` 启动时执行一次 |

## 2. 通道配置

在 UI「通知管理 → 通知配置」里新增。每条配置包含：

- **配置名称**：UI 上显示用，建议带场景，如 `生产-钉钉告警群`
- **通道类型**：4 选 1
- **通道配置**：JSON 字符串，按通道类型不同字段不同（见下表）
- **订阅事件**：勾选要触发该通知的事件
- **限定服务器组 / 任务模板**：可选，限定后只对这部分命中
- **启用开关**：停用后不投递但保留历史可查

### 2.1 钉钉（dingtalk）

```json
{
  "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=xxxx",
  "secret": "SEC...（加签密钥，可选）"
}
```

`secret` 留空则不签名（明文机器人）。填了就走 HMAC-SHA256 签名。

### 2.2 企业微信（wechat_work）

```json
{
  "webhookUrl": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxx"
}
```

只支持群机器人 webhook（text 类型），不支持应用消息/卡片。

### 2.3 自定义 Webhook

```json
{
  "url": "https://hooks.example.com/notify",
  "headers": {
    "Authorization": "Bearer xxx",
    "X-Source": "redeploy"
  }
}
```

`headers` 可选。请求体固定为 `{"title": ..., "body": ..., "eventType": ..., "occurredAt": "..."}`。

### 2.4 邮件（email）

```json
{
  "from": "noreply@example.com",
  "to": "ops@example.com,dev@example.com",
  "cc": "manager@example.com",
  "subjectPrefix": "[ReDeploy]"
}
```

`cc` 可选，`subjectPrefix` 可选。SMTP 服务由 `spring.mail.*` 全局配置（见 application.yml），所有邮件通道共享同一 SMTP。

## 3. 事件类型

| 事件 | 触发时机 | 适用 |
| --- | --- | --- |
| `TASK_STARTED` | 部署任务进入 `deployToServer` 之前 | 想看到任务开始通知 |
| `TASK_SUCCESS` | 所有服务器部署成功 | 成功播报 |
| `TASK_FAILED` | 任一服务器部署失败 / Jenkins 前置失败 | **推荐必开**，对应老「部署失败告警」 |
| `TASK_CANCELLED` | 任务被取消（待 task-execution cancel 支持） | 灰度阶段可不开 |
| `STEP_FAILED` | agent 单个 shell 步骤失败 | 调试阶段 |
| `STEP_TIMEOUT` | agent 单个步骤超时 | 调试阶段 |
| `AGENT_ONLINE` | agent 心跳恢复 | 监控 agent 存活 |
| `AGENT_OFFLINE` | agent 心跳超时 | 监控 agent 存活 |

## 4. 限定维度

- **服务器组**：选了就只对属于这些分组的服务器事件触发。适合「生产服务器失败才发钉钉，测试服务器不发」。
- **任务模板**：选了就只对属于这些任务模板的事件触发。适合「只关心订单系统的部署」。

两个维度同时设置时取交集（AND）。

## 5. 发送历史

UI 第二个 tab「发送历史」展示所有历史投递记录，包含：

- 事件类型 / 通道类型 / 任务 ID
- 状态：`success` / `failed`
- 错误信息（失败时）
- 发送时间

支持按配置 / 事件 / 状态过滤。

后台每天 03:30 清理 30 天前历史，可由 `REDEPLOY_NOTIFICATION_HISTORY_RETENTION_DAYS` 调整。

## 6. 测试发送

每个配置右侧「测试」按钮弹窗输入正文，会真发一条到对应通道（不走重试、不写历史外的特殊逻辑）。建议每次新建配置都先测一次再启用订阅。

## 7. 从老 yml 配置迁移

`application.yml` 里的 `redeploy.dingtalk.*` 字段已标记为 deprecated，但**仍可生效**：

- 服务启动时 `NotificationConfigMigrator` 会读 yml → 自动插入一条等价的 `notification_config`（订阅 `TASK_FAILED`，`failure-only` 模式；`all` 模式订阅四个任务事件）
- 启动日志会打印 WARN：「已自动从 yml 迁移钉钉告警配置到 notification_config」
- DB 中已经存在钉钉类型配置时跳过迁移（不会覆盖用户在 UI 上做的修改）

迁移完成后请在 UI 上校验，配置正确后**手动删除 yml 字段**（计划下个版本移除）。

## 8. 故障排查

| 现象 | 排查 |
| --- | --- |
| 测试发送失败 | 看服务端日志，钉钉/企微一般会回 310000 系列错误码；邮件看 SMTP 错误 |
| 部署失败但没收到通知 | 检查「发送历史」里有没有失败记录；查看「订阅事件」是否包含 `TASK_FAILED`；检查是否限定了服务器组/任务模板把当前部署过滤掉 |
| 收件重复 | 多个通知配置都订阅了同一事件；删掉重复的或在「限定维度」里区分 |
| 邮件测试失败：邮件通道未启用 | 启动未加载 `spring-boot-starter-mail` 依赖或 `spring.mail.*` 未配置（pom 已包含依赖，需配 host/port/user/pass） |

## 9. API 速查

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/notification/channels` | 通道类型清单 |
| GET | `/api/notification/event-types` | 事件类型清单 |
| GET | `/api/notification/configs` | 配置列表 |
| GET | `/api/notification/configs/{id}` | 单条配置 |
| POST | `/api/notification/configs` | 新建 |
| PUT | `/api/notification/configs/{id}` | 更新 |
| DELETE | `/api/notification/configs/{id}` | 删除 |
| POST | `/api/notification/configs/{id}/test` | 测试发送（body 可选 `{body: "..."}`） |
| GET | `/api/notification/history` | 历史分页（`configId` / `eventType` / `status` / `limit` / `offset`） |

所有接口需 `Authorization: Bearer <adminToken>` 头。
