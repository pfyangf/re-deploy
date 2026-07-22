package com.redeploy.controller;

import com.redeploy.model.DeployHistory;
import com.redeploy.model.Server;
import com.redeploy.model.Task;
import com.redeploy.repository.DeployHistoryMapper;
import com.redeploy.repository.ServerMapper;
import com.redeploy.repository.TaskMapper;
import com.redeploy.service.DeployService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deploy")
public class DeployController {

    @Autowired
    private DeployHistoryMapper deployHistoryMapper;

    @Autowired
    private ServerMapper serverMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private DeployService deployService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> triggerDeploy(@RequestBody DeployRequest request) {
        // Validate task exists
        Task task = taskMapper.findById(request.getTaskId())
                .orElse(null);
        if (task == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Task not found"));
        }

        // Validate servers exist
        List<Server> servers = new ArrayList<>();
        for (Long serverId : request.getServerIds()) {
            serverMapper.findById(serverId).ifPresent(servers::add);
        }
        if (servers.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No valid servers found"));
        }

        // Create deploy history
        DeployHistory history = new DeployHistory();
        history.setTaskId(request.getTaskId());
        history.setServerIds(request.getServerIds().toString());
        // Auto-fill version from Jenkins build number when user didn't provide one
        String version = request.getVersion();
        if ((version == null || version.isEmpty()) && request.getParams() != null) {
            String buildNumber = request.getParams().get("jenkinsBuildNumber");
            if (buildNumber != null && !buildNumber.isEmpty()) {
                version = "#" + buildNumber;
            }
        }
        history.setVersion(version);
        history.setStatus("running");
        history.setStartedAt(LocalDateTime.now());
        deployHistoryMapper.insert(history);

        // Start deployment in background
        deployService.deploy(history, task, servers, request.getParams());

        return ResponseEntity.ok(Map.of(
            "deployId", history.getId(),
            "status", "running"
        ));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DeployHistory> getDeployStatus(@PathVariable Long id) {
        return deployHistoryMapper.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public List<DeployHistory> getDeployHistory(
            @RequestParam(required = false) Long serverId,
            @RequestParam(required = false) String status) {
        List<DeployHistory> list = (status != null)
                ? deployHistoryMapper.findByStatus(status)
                : deployHistoryMapper.findAllOrderByCreatedAtDesc();
        // Populate task name for UI display
        java.util.Map<Long, String> taskNameCache = new java.util.HashMap<>();
        for (DeployHistory h : list) {
            if (h.getTaskId() == null) continue;
            String name = taskNameCache.get(h.getTaskId());
            if (name == null) {
                name = taskMapper.findById(h.getTaskId()).map(Task::getName).orElse(null);
                taskNameCache.put(h.getTaskId(), name);
            }
            h.setTaskName(name);
        }
        return list;
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, String>> cancelDeploy(@PathVariable Long id) {
        return deployHistoryMapper.findById(id)
                .map(history -> {
                    if ("running".equals(history.getStatus())) {
                        history.setStatus("cancelled");
                        history.setCompletedAt(LocalDateTime.now());
                        deployHistoryMapper.update(history);
                        return ResponseEntity.ok(Map.of("status", "cancelled"));
                    }
                    return ResponseEntity.badRequest().body(Map.of("error", "Deploy is not running"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupHistory() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<DeployHistory> oldRecords = deployHistoryMapper.findByCreatedAtBefore(threshold);
        int count = oldRecords.size();
        deployHistoryMapper.deleteByCreatedAtBefore(threshold);
        return ResponseEntity.ok(Map.of("deleted", count));
    }

    public static class DeployRequest {
        private Long taskId;
        private List<Long> serverIds;
        private String version;
        private Map<String, String> params;

        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public List<Long> getServerIds() { return serverIds; }
        public void setServerIds(List<Long> serverIds) { this.serverIds = serverIds; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public Map<String, String> getParams() { return params; }
        public void setParams(Map<String, String> params) { this.params = params; }
    }
}
