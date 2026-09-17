package com.redeploy.model;

import java.time.LocalDateTime;

/**
 * 系统设置 — key-value 形式存储运行时可改的全局参数。
 * 首个设置项：concurrent_deploy_limit（同时执行的部署数，超出自动排队）。
 */
public class SystemSetting {

    private Long id;
    private String key;
    private String value;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SystemSetting() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
