package com.redeploy.model;

import java.time.LocalDateTime;

/**
 * 通知发送历史 — 每次发送尝试落一条记录，30 天后清理。
 * target 字段做脱敏（webhook URL 只留域名，邮箱地址保留完整以便排查）。
 */
public class NotificationHistory {

    private Long id;
    private Long configId;
    private String eventType;
    private Long serverId;
    private Long taskId;
    private Long deployHistoryId;
    private String channelType;
    private String target;           // 脱敏后的目标（webhook 域名 / 邮箱地址）
    private String payload;          // 实际发送的 JSON 内容
    private String status;           // success | failed | retrying
    private String errorMessage;
    private LocalDateTime sentAt;

    public NotificationHistory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getDeployHistoryId() { return deployHistoryId; }
    public void setDeployHistoryId(Long deployHistoryId) { this.deployHistoryId = deployHistoryId; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}