package com.redeploy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redeploy.model.DeployHistory;
import com.redeploy.model.Group;
import com.redeploy.model.Server;
import com.redeploy.model.Task;
import com.redeploy.repository.DeployHistoryMapper;
import com.redeploy.repository.GroupMapper;
import com.redeploy.repository.ServerMapper;
import com.redeploy.repository.TaskMapper;
import com.redeploy.service.DeployService;
import com.redeploy.service.JenkinsService;
import com.redeploy.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private GroupMapper groupMapper;

    @Autowired
    private DeployService deployService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("\\d+");
    private static final Pattern BUILD_NUMBER_PATTERN = Pattern.compile("^#(\\d+)$");

    @Autowired
    private JenkinsService jenkinsService;

    @Autowired
    private AgentService agentService;

    private static final int PRECHECK_THREADS = 10;

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

        // Pre-check: verify all servers are online
        List<Server> offlineServers = precheckServers(servers);
        if (!offlineServers.isEmpty()) {
            String names = offlineServers.stream()
                    .map(s -> s.getName() + " (" + s.getHost() + ")")
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "以下服务器离线: " + names
            ));
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
        history.setRequestParams(snapshotRequest(request, task, servers));
        // 先落 pending：部署在专用线程池中按系统设置的并发数执行，超出的任务排队等待
        history.setStatus("pending");
        history.setStartedAt(LocalDateTime.now());
        deployHistoryMapper.insert(history);

        // Start deployment in background
        deployService.deploy(history, task, servers, request.getParams());

        return ResponseEntity.ok(Map.of(
            "deployId", history.getId(),
            "status", "pending"
        ));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DeployHistory> getDeployStatus(@PathVariable Long id) {
        return deployHistoryMapper.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<DeployHistory> getDeployDetail(@PathVariable Long id) {
        return deployHistoryMapper.findByIdWithDetail(id)
                .map(history -> {
                    fillDeployContext(history);
                    return ResponseEntity.ok(history);
                })
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
                    String status = history.getStatus();
                    if ("running".equals(status) || "pending".equals(status)) {
                        // pending：任务还在队列中，DeployService 取出时会检测到 cancelled 而跳过；
                        // running：仅标记取消（执行中的轮询不中断，终态写回时不会覆盖取消状态）
                        deployHistoryMapper.updateStatus(id, "cancelled", LocalDateTime.now());
                        return ResponseEntity.ok(Map.of("status", "cancelled"));
                    }
                    return ResponseEntity.badRequest().body(Map.of("error", "Deploy is not running or pending"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jenkins/builds")
    public ResponseEntity<?> getJenkinsBuilds(
            @RequestParam Long taskId,
            @RequestParam(defaultValue = "20") int limit) {
        Task task = taskMapper.findById(taskId).orElse(null);
        if (task == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Task not found"));
        }
        if (task.getJenkinsEnabled() == null || !task.getJenkinsEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Jenkins is not enabled for this task"));
        }
        if (task.getJenkinsUrl() == null || task.getJenkinsJobName() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Jenkins configuration is incomplete"));
        }
        try {
            List<Map<String, Object>> builds = jenkinsService.getBuildHistory(
                    task.getJenkinsUrl(),
                    task.getJenkinsJobName(),
                    task.getJenkinsUser(),
                    task.getJenkinsToken(),
                    limit
            );
            return ResponseEntity.ok(builds);
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "Failed to fetch builds from Jenkins: " + e.getMessage()));
        }
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupHistory() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<DeployHistory> oldRecords = deployHistoryMapper.findByCreatedAtBefore(threshold);
        int count = oldRecords.size();
        deployHistoryMapper.deleteByCreatedAtBefore(threshold);
        return ResponseEntity.ok(Map.of("deleted", count));
    }

    private List<Server> precheckServers(List<Server> servers) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(servers.size(), PRECHECK_THREADS));
        try {
            List<CompletableFuture<ServerCheckResult>> futures = servers.stream()
                    .map(server -> CompletableFuture.supplyAsync(() -> {
                        boolean online = agentService.testConnection(server);
                        server.setStatus(online ? "online" : "offline");
                        serverMapper.update(server);
                        return new ServerCheckResult(server, online);
                    }, executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(r -> !r.online)
                    .map(r -> r.server)
                    .collect(Collectors.toList());
        } finally {
            executor.shutdown();
        }
    }

    private static class ServerCheckResult {
        final Server server;
        final boolean online;

        ServerCheckResult(Server server, boolean online) {
            this.server = server;
            this.online = online;
        }
    }

    /**
     * 记下部署当时选的分组、任务、构建号和目标服务器，供历史详情展示。
     * 分组未传时记为「全部分组」。
     */
    private String snapshotRequest(DeployRequest request, Task task, List<Server> servers) {
        Map<Long, String> groupNames = new LinkedHashMap<>();
        Map<String, Object> snap = new LinkedHashMap<>();
        Long groupId = request.getGroupId();
        snap.put("groupId", groupId);
        if (groupId == null) {
            snap.put("groupName", "全部分组");
        } else {
            snap.put("groupName", groupName(groupId, groupNames));
        }
        snap.put("taskName", task.getName());
        String buildNumber = null;
        if (request.getParams() != null) {
            String raw = request.getParams().get("jenkinsBuildNumber");
            if (raw != null && !raw.isBlank()) {
                buildNumber = raw.trim();
            }
        }
        snap.put("jenkinsBuildNumber", buildNumber);
        List<Map<String, Object>> targets = new ArrayList<>();
        for (Server server : servers) {
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("id", server.getId());
            target.put("name", server.getName());
            target.put("host", server.getHost());
            target.put("port", server.getPort());
            target.put("groupName", server.getGroupId() == null ? null : groupName(server.getGroupId(), groupNames));
            targets.add(target);
        }
        snap.put("servers", targets);
        try {
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            return null;
        }
    }

    private String groupName(Long groupId, Map<Long, String> cache) {
        if (cache.containsKey(groupId)) {
            return cache.get(groupId);
        }
        String name = groupMapper.findById(groupId).map(Group::getName).orElse("分组#" + groupId);
        cache.put(groupId, name);
        return name;
    }

    /** 用快照填充详情；没有快照的旧记录按任务和服务器当前资料还原。 */
    private void fillDeployContext(DeployHistory history) {
        Task task = history.getTaskId() == null ? null : taskMapper.findById(history.getTaskId()).orElse(null);
        if (task != null) {
            history.setTaskName(task.getName());
        }
        applySnapshot(history);
        if (history.getTargets() == null) {
            history.setTargets(resolveTargets(history.getServerIds()));
        }
        if (history.getGroupName() == null || history.getGroupName().isBlank()) {
            history.setGroupName(inferGroupName(history.getTargets(), task));
        }
        if (history.getJenkinsBuildNumber() == null || history.getJenkinsBuildNumber().isBlank()) {
            history.setJenkinsBuildNumber(inferBuildNumber(history.getVersion()));
        }
    }

    private void applySnapshot(DeployHistory history) {
        if (history.getRequestParams() == null || history.getRequestParams().isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(history.getRequestParams());
            if (node.hasNonNull("groupName")) {
                history.setGroupName(node.get("groupName").asText());
            }
            if (node.hasNonNull("jenkinsBuildNumber")) {
                String build = node.get("jenkinsBuildNumber").asText();
                if (!build.isBlank()) {
                    history.setJenkinsBuildNumber(build);
                }
            }
            if ((history.getTaskName() == null || history.getTaskName().isBlank()) && node.hasNonNull("taskName")) {
                history.setTaskName(node.get("taskName").asText());
            }
            JsonNode servers = node.get("servers");
            if (servers != null && servers.isArray()) {
                List<DeployHistory.Target> targets = new ArrayList<>();
                for (JsonNode item : servers) {
                    DeployHistory.Target target = new DeployHistory.Target();
                    if (item.hasNonNull("id")) target.setId(item.get("id").asLong());
                    if (item.hasNonNull("name")) target.setName(item.get("name").asText());
                    if (item.hasNonNull("host")) target.setHost(item.get("host").asText());
                    if (item.hasNonNull("port")) target.setPort(item.get("port").asInt());
                    if (item.hasNonNull("groupName")) target.setGroupName(item.get("groupName").asText());
                    targets.add(target);
                }
                history.setTargets(targets);
            }
        } catch (Exception ignored) {
            // 快照损坏时走下面的现查逻辑
        }
    }

    private List<DeployHistory.Target> resolveTargets(String serverIds) {
        List<DeployHistory.Target> targets = new ArrayList<>();
        if (serverIds == null || serverIds.isBlank()) {
            return targets;
        }
        Map<Long, String> groupNames = new LinkedHashMap<>();
        Matcher matcher = SERVER_ID_PATTERN.matcher(serverIds);
        while (matcher.find()) {
            Long id = Long.valueOf(matcher.group());
            DeployHistory.Target target = new DeployHistory.Target();
            target.setId(id);
            serverMapper.findById(id).ifPresentOrElse(server -> {
                target.setName(server.getName());
                target.setHost(server.getHost());
                target.setPort(server.getPort());
                if (server.getGroupId() != null) {
                    target.setGroupName(groupName(server.getGroupId(), groupNames));
                }
            }, () -> target.setName("服务器#" + id));
            targets.add(target);
        }
        return targets;
    }

    private String inferGroupName(List<DeployHistory.Target> targets, Task task) {
        Set<String> names = new LinkedHashSet<>();
        if (targets != null) {
            for (DeployHistory.Target target : targets) {
                if (target.getGroupName() != null && !target.getGroupName().isBlank()) {
                    names.add(target.getGroupName());
                }
            }
        }
        if (names.size() == 1) {
            return names.iterator().next();
        }
        if (names.size() > 1) {
            return String.join("、", names);
        }
        if (task != null && task.getGroupId() != null) {
            return groupMapper.findById(task.getGroupId()).map(Group::getName).orElse("分组#" + task.getGroupId());
        }
        return "—";
    }

    private String inferBuildNumber(String version) {
        if (version == null) return null;
        Matcher matcher = BUILD_NUMBER_PATTERN.matcher(version.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    public static class DeployRequest {
        private Long taskId;
        private List<Long> serverIds;
        private String version;
        private Long groupId;
        private Map<String, String> params;

        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public List<Long> getServerIds() { return serverIds; }
        public void setServerIds(List<Long> serverIds) { this.serverIds = serverIds; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public Map<String, String> getParams() { return params; }
        public void setParams(Map<String, String> params) { this.params = params; }
    }
}
