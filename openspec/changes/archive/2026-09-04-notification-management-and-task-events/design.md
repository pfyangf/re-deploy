## Context

`alert-notification` 是 re-deploy 第一版就有的能力，硬编码钉钉 webhook 在 `application.yml` 里，触发点只有「部署失败」一个。日常运维中运维同事反映「想给领导群发的告警和开发群分开」「没有钉钉的客户端用邮件」「任务跑了两小时没人推成功消息」。需要把这块抽象成独立子系统。

事件源主要来自：
- `DeployService` 部署任务整体状态变化（开始/成功/失败/超时/取消）
- `TaskExecutor` 任务步骤状态变化（步骤开始/步骤成功/步骤失败/步骤超时）
- `AgentHeartbeatService` agent 上下线（agent.online / agent.offline）

下游需要按服务器组、任务模板、事件类型三个维度组合订阅。

## Goals / Non-Goals

**Goals:**
- 把钉钉配置从 yml 挪到运行时可管理的表里
- 支持钉钉、企微、自定义 webhook、邮件 4 种通道
- 每条通知配置可独立选择订阅事件、目标服务器组、目标任务模板
- 通知发送历史可查询（最近 N 条）
- 支持「测试发送」（不依赖真实事件触发）
- 不破坏现有「部署失败钉钉告警」行为

**Non-Goals:**
- 不做用户/权限管理（沿用 admin-token 单用户模型）
- 不做消息模板可视化编辑（提供内置模板 + 占位符即可）
- 不做飞书/Slack/PagerDuty 等额外通道（接口预留，后续按需实现）
- 不做告警聚合/抑制（一条事件可能匹配多条配置，全部发送）
- 不做 IM 富文本卡片（钉钉用 markdown，企微用 text，邮件用 HTML）

## Decisions

### D1: 通道接口设计

```java
public interface NotificationChannel {
    String type();                          // "dingtalk" | "wechatwork" | "webhook" | "email"
    NotificationResult send(NotificationMessage msg, NotificationConfig config);
}
```

每种通道单独实现类（`DingTalkChannel` / `WechatWorkChannel` / `WebhookChannel` / `EmailChannel`），由 `NotificationDispatcher` 按 `config.channelType` 路由。

**理由**：Spring `@Component` 自动注入 `Map<String, NotificationChannel>`，新增通道零改动 dispatcher。

### D2: 事件模型

```java
public class SystemEvent {
    String eventType;       // "task.started" | "task.success" | "task.failed" | ...
    Long serverId;
    Long taskId;
    String taskName;
    Long deployHistoryId;
    String version;
    String errorMessage;    // 失败时
    Instant occurredAt;
    Map<String, Object> extras;
}
```

7 类事件：
- `task.started` — 部署任务开始
- `task.success` — 部署任务成功
- `task.failed` — 部署任务失败（终态）
- `step.failed` — 单个步骤失败
- `step.timeout` — 单个步骤超时
- `agent.online` — agent 重新心跳恢复
- `agent.offline` — agent 心跳超时

事件用 Spring `ApplicationEventPublisher` 发布，监听器由 `NotificationDispatcher` 实现。

### D3: 订阅匹配规则

`notification_config` 表核心字段：
```
id, channel_type, channel_config(JSON), name, enabled,
event_types(JSON array),            -- ["task.failed", "step.failed"]
server_group_ids(JSON array, nullable), -- 限定哪些服务器组触发
task_template_ids(JSON array, nullable), -- 限定哪些任务模板触发
created_at, updated_at
```

匹配逻辑：事件 → 查 enabled=1 的 config → 校验 eventType ⊆ config.event_types → 若 config 设了 server_group_ids 则事件源 serverId 必须在某组内 → 若设了 task_template_ids 则任务模板必须在列表内 → 全部通过则入队发送。

**理由**：三个维度组合才能精确过滤；任一维度为空表示「不限」。

### D4: 钉钉签名算法

沿用现有实现：`secret` 存在 `channel_config` JSON 里，加签 URL = webhook + `&timestamp=...&sign=...`（HMAC-SHA256）。

**BREAKING 兼容**：`redeploy.dingtalk.secret` 配置项移除；存量配置需要手动迁移。

### D5: 邮件通道依赖

新增 Maven 依赖：`spring-boot-starter-mail`（Spring Boot 自带 starter，不增加额外维护成本）。

邮件通道 config：
```json
{
  "smtpHost": "smtp.example.com",
  "smtpPort": 465,
  "username": "alert@example.com",
  "password": "***",
  "useSsl": true,
  "from": "alert@example.com",
  "to": ["dev1@example.com", "dev2@example.com"],
  "subjectTemplate": "[redeploy] {{taskName}} 部署{{status}}"
}
```

**非目标**：不内置发件服务器配置向导。

### D7: 发送历史表

`notification_history` 表字段：
```
id, config_id, event_type, server_id, task_id, deploy_history_id,
channel_type, target(JSON, 脱敏), payload(JSON), status, error_message, sent_at
```

- `target`：通道目标（如 webhook URL 域名、邮箱地址）
- `status`：`success` / `failed` / `retrying`
- 保留 30 天，`@Scheduled` 每天凌晨清理

### D8: 失败重试

沿用现有 3 次指数退避。在 `NotificationDispatcher` 统一处理，不下沉到各通道实现。

### D9: 「测试发送」接口

`POST /api/notifications/{id}/test` → 构造一条伪造的 `task.failed` 事件（任务名 `测试发送`，无 serverId）→ 走完整发送流程 → 返回 `notification_history` 中的最新一条记录。

**理由**：配置 webhook URL / SMTP 后立刻验证；不触发真实事件。

### D10: 配置迁移脚本

`scripts/migrate-notification-config.sh`：
- 读 `application.yml` 中的 `redeploy.dingtalk.webhook-url` / `redeploy.dingtalk.secret`
- 插入一条 `notification_config`：`channel_type=dingtalk`、`channel_config={webhookUrl, secret}`、`event_types=["task.failed"]`
- 输出迁移结果，让用户手动校验后删除 yml 字段

## Risks / Trade-offs

- **[风险]** 配置迁移漏迁移 → 老环境升级后静默丢失告警 → 缓解：迁移脚本打印原值；首次启动检测到 yml 里有 webhook 配置但 `notification_config` 表为空时打印 WARN 日志
- **[风险]** 邮件 SMTP 凭据明文存 DB → 缓解：`channel_config` 列后续可考虑加密（当前不变，先跑通流程）
- **[风险]** 一事件可能匹配多条 → 一时刻几十条 webhook → 缓解：dispatcher 内单 config 串行（避免一个钉钉群组并发风暴），跨 config 并行
- **[取舍]** 不做权限隔离 → 所有 admin-token 持有者都能改所有通知配置 → 接受（沿用单用户模型）
- **[取舍]** 不做模板可视化 → 模板硬编码在通道实现里 → 接受（占位符足够）
- **[取舍]** 通知历史只存 30 天 → 历史归档不做 → 接受（数据量可控）

## 项目结构变化

```
server/src/main/java/com/redeploy/
├── notification/
│   ├── NotificationChannel.java          # 接口
│   ├── channel/
│   │   ├── DingTalkChannel.java
│   │   ├── WechatWorkChannel.java
│   │   ├── WebhookChannel.java
│   │   └── EmailChannel.java
│   ├── NotificationDispatcher.java       # 主入口
│   ├── NotificationConfig.java          # 实体
│   ├── NotificationHistory.java          # 实体
│   ├── SystemEvent.java                  # 事件模型
│   └── ...
├── controller/
│   ├── NotificationController.java       # 新增
│   └── ...
├── repository/
│   ├── NotificationConfigMapper.java     # 新增
│   ├── NotificationHistoryMapper.java     # 新增
│   └── ...
└── service/
    ├── DeployService.java                # 改造：发布事件代替直接 alert 调用
    ├── AlertService.java                 # 移除（或标记 deprecated）
    └── ...
```

## API 设计概要

```
# 通知配置 CRUD
GET    /api/notifications                  # 列表（分页）
POST   /api/notifications                  # 创建
GET    /api/notifications/{id}             # 详情
PUT    /api/notifications/{id}             # 更新
DELETE /api/notifications/{id}             # 删除
POST   /api/notifications/{id}/test        # 测试发送

# 通知历史
GET    /api/notifications/history          # 列表（按 configId / 时间范围过滤）
GET    /api/notifications/history/{id}     # 详情
```

## Open Questions

1. **事件类型粒度**：是否需要「任务取消」事件？目前 task-execution spec 已支持 cancel 接口，建议加 `task.cancelled`
2. **多语言通知**：邮件/钉钉消息模板要不要 i18n？目前默认中文，跟 README 一致
3. **webhook 安全**：自定义 webhook 通道要不要支持加签头（HMAC）由用户配置？目前默认只发 payload