package com.redeploy.model;

import java.time.LocalDateTime;

public class DeployHistory {

    private Long id;
    private Long taskId;
    private String serverIds;
    private String version;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private String logs;
    private LocalDateTime createdAt;

    // Transient: filled by controller from tasks table for UI display, not persisted
    private String taskName;

    public DeployHistory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getServerIds() { return serverIds; }
    public void setServerIds(String serverIds) { this.serverIds = serverIds; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getLogs() { return logs; }
    public void setLogs(String logs) { this.logs = logs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
}
