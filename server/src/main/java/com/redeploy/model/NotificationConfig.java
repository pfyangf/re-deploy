package com.redeploy.model;

import java.time.LocalDateTime;

/**
 * 通知配置 — 运行时可管理的通知通道定义。
 *
 * channel_config、event_types、server_group_ids、task_template_ids 均为 JSON 字符串，
 * 由 service 层用 Jackson 序列化/反序列化；DB 列用 TEXT 存储。
 */
public class NotificationConfig {

    private Long id;
    private String name;
    private String channelType;        // dingtalk | wechatwork | webhook | email
    private String channelConfig;      // 通道特定配置 JSON
    private String eventTypes;         // 订阅事件类型 JSON 数组
    private String serverGroupIds;     // 限定服务器组 JSON 数组，null 表示不限
    private String taskTemplateIds;    // 限定任务模板 JSON 数组，null 表示不限
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NotificationConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getChannelConfig() { return channelConfig; }
    public void setChannelConfig(String channelConfig) { this.channelConfig = channelConfig; }

    public String getEventTypes() { return eventTypes; }
    public void setEventTypes(String eventTypes) { this.eventTypes = eventTypes; }

    public String getServerGroupIds() { return serverGroupIds; }
    public void setServerGroupIds(String serverGroupIds) { this.serverGroupIds = serverGroupIds; }

    public String getTaskTemplateIds() { return taskTemplateIds; }
    public void setTaskTemplateIds(String taskTemplateIds) { this.taskTemplateIds = taskTemplateIds; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}