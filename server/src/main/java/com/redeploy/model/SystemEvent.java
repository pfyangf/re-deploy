package com.redeploy.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 系统事件 — 通过 ApplicationEventPublisher 发布，由 NotificationDispatcher 订阅。
 *
 * 8 种事件类型见下方 TYPE_* 常量。
 *
 * 字段约定：
 *  - TASK_*：fill  task / history / serverIds（参与部署的全部 server ID）/ serverResults
 *  - STEP_*：fill  taskId / stepName / reason
 *  - AGENT_*：fill server / reason（offline 时填）
 */
public class SystemEvent {

    public static final String TYPE_TASK_STARTED = "TASK_STARTED";
    public static final String TYPE_TASK_SUCCESS = "TASK_SUCCESS";
    public static final String TYPE_TASK_FAILED = "TASK_FAILED";
    public static final String TYPE_TASK_CANCELLED = "TASK_CANCELLED";
    public static final String TYPE_STEP_FAILED = "STEP_FAILED";
    public static final String TYPE_STEP_TIMEOUT = "STEP_TIMEOUT";
    public static final String TYPE_AGENT_ONLINE = "AGENT_ONLINE";
    public static final String TYPE_AGENT_OFFLINE = "AGENT_OFFLINE";

    private String type;
    private Task task;
    private DeployHistory history;
    private Set<Long> serverIds = new HashSet<>();
    private Map<String, Boolean> serverResults = new LinkedHashMap<>(); // serverName -> success
    private Server server;                 // AGENT_xxx 事件专用
    private Long taskId;                   // 步骤级事件可直接传
    private String stepName;
    private String reason;
    private Instant occurredAt = Instant.now();

    public SystemEvent() {}

    public static SystemEvent of(String type) {
        SystemEvent e = new SystemEvent();
        e.type = type;
        e.occurredAt = Instant.now();
        return e;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public DeployHistory getHistory() { return history; }
    public void setHistory(DeployHistory history) { this.history = history; }

    public Set<Long> getServerIds() { return serverIds; }
    public void setServerIds(Set<Long> serverIds) { this.serverIds = serverIds; }

    public Map<String, Boolean> getServerResults() { return serverResults; }
    public void setServerResults(Map<String, Boolean> serverResults) { this.serverResults = serverResults; }

    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
