## ADDED Requirements

### Requirement: Notification channel abstraction
The system SHALL provide a pluggable notification channel architecture.

#### Scenario: Channel interface
- **WHEN** the server needs to send a notification
- **THEN** the system routes to the implementation matching the `channel_type` of the notification config
- **AND** each channel implements `NotificationChannel.send(message, config)` returning `NotificationResult`

#### Scenario: Supported channel types
- **WHEN** configuring a notification
- **THEN** the system SHALL support four channel types: `dingtalk`, `wechatwork`, `webhook`, `email`
- **AND** each type has its own `channel_config` JSON schema

### Requirement: Notification config CRUD
The system SHALL provide CRUD APIs for notification configurations.

#### Scenario: Create notification config
- **WHEN** `POST /api/notifications` is called with channel_type, channel_config, name, event_types, and optional server_group_ids / task_template_ids
- **THEN** the system persists a new `notification_config` row and returns its id

#### Scenario: Update notification config
- **WHEN** `PUT /api/notifications/{id}` is called
- **THEN** the system updates the corresponding row

#### Scenario: Disable notification config
- **WHEN** `enabled=false` is set on a config (either via update or direct toggle)
- **THEN** the dispatcher SHALL skip this config when matching events

#### Scenario: List notification configs
- **WHEN** `GET /api/notifications` is called
- **THEN** the system returns paginated list of all configs (including disabled)

### Requirement: Test send
The system SHALL allow sending a test notification without triggering a real event.

#### Scenario: Test a notification config
- **WHEN** `POST /api/notifications/{id}/test` is called
- **THEN** the system constructs a synthetic `task.failed` event with placeholder task name "测试发送"
- **AND** dispatches it through the normal pipeline
- **AND** returns the resulting `notification_history` row

### Requirement: DingTalk channel
The DingTalk channel SHALL send Markdown messages to a DingTalk robot webhook with optional signing.

#### Scenario: Send signed DingTalk message
- **WHEN** `channel_config.secret` is present
- **THEN** the system computes `sign = HMAC-SHA256(secret, "${timestamp}\n${secret}")` and appends `timestamp` + `sign` to the webhook URL

#### Scenario: Send unsigned DingTalk message
- **WHEN** `channel_config.secret` is empty or absent
- **THEN** the system posts the webhook URL with payload only

#### Scenario: DingTalk payload
- **WHEN** sending a DingTalk message
- **THEN** payload `msgtype` is `markdown` and `markdown.title` + `markdown.text` include task name, server, status, error, timestamp

### Requirement: WeChat Work channel
The WeChat Work channel SHALL send text messages to a WeChat Work robot webhook.

#### Scenario: Send WeChat Work message
- **WHEN** `channel_config.webhookUrl` is set
- **THEN** the system POSTs `{ "msgtype": "text", "text": { "content": "..." } }` to the webhook

### Requirement: Webhook channel
The Webhook channel SHALL send arbitrary JSON POST to a user-defined URL.

#### Scenario: Send webhook payload
- **WHEN** `channel_config.url` is set
- **THEN** the system POSTs the notification message as JSON body to the URL
- **AND** merges `channel_config.headers` into the request headers

### Requirement: Email channel
The Email channel SHALL send HTML emails via SMTP.

#### Scenario: Send email
- **WHEN** `channel_config.smtpHost/Port/username/password/useSsl/from/to/subjectTemplate` are set
- **THEN** the system connects to SMTP and sends an HTML email to all addresses in `to`
- **AND** renders the template by substituting `{{taskName}}`, `{{status}}`, `{{errorMessage}}`, `{{timestamp}}`, `{{version}}`, `{{serverName}}`

### Requirement: Notification retry
The dispatcher SHALL retry failed sends up to 3 times with exponential backoff.

#### Scenario: Retry on send failure
- **WHEN** a channel `send()` returns failure or throws
- **THEN** the system retries after 1s, 2s, 4s (exponential backoff)
- **AND** after 3 failures marks the `notification_history` row as `failed` with the last error message

### Requirement: Notification history
The system SHALL persist every send attempt for audit.

#### Scenario: Record send result
- **WHEN** a notification is sent (success or failure)
- **THEN** the system inserts a `notification_history` row containing config_id, event_type, server_id, task_id, channel_type, target (redacted), payload, status, error_message, sent_at

#### Scenario: Query history
- **WHEN** `GET /api/notifications/history?configId=X&from=Y&to=Z` is called
- **THEN** the system returns matching history rows ordered by `sent_at` DESC

#### Scenario: Cleanup old history
- **WHEN** a scheduled task runs daily at 03:00
- **THEN** the system deletes `notification_history` rows where `sent_at < NOW() - 30 day`

### Requirement: Channel-type-specific config validation
The system SHALL validate `channel_config` shape against the channel type on create/update.

#### Scenario: DingTalk config validation
- **WHEN** creating a DingTalk config
- **THEN** `channel_config.webhookUrl` is required
- **AND** `channel_config.secret` is optional

#### Scenario: Email config validation
- **WHEN** creating an Email config
- **THEN** `channel_config.smtpHost`, `smtpPort`, `username`, `password`, `from`, `to` are all required
- **AND** `to` MUST contain at least one address

#### Scenario: Invalid config rejection
- **WHEN** required fields are missing for the selected channel type
- **THEN** the API returns `400` with a field-level error message