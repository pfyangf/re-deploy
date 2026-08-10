package com.redeploy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redeploy.model.DeployHistory;
import com.redeploy.model.Server;
import com.redeploy.model.Task;
import com.redeploy.repository.DeployHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);

    @Autowired
    private DeployHistoryMapper deployHistoryMapper;

    @Autowired
    private FileTransferService fileTransferService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private JenkinsService jenkinsService;

    @Autowired
    private ArtifactService artifactService;

    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        // 强制 StringHttpMessageConverter 用 UTF-8，避免 agent 返回的 ndjson 中文被按 ISO-8859-1 解码乱码
        rt.getMessageConverters().stream()
                .filter(c -> c instanceof StringHttpMessageConverter)
                .map(c -> (StringHttpMessageConverter) c)
                .forEach(c -> c.setDefaultCharset(java.nio.charset.StandardCharsets.UTF_8));
        return rt;
    }

    @Async
    public void deploy(DeployHistory history, Task task, List<Server> servers, Map<String, String> params) {
        log.info("Starting deployment {} for task '{}' to {} servers",
                history.getId(), task.getName(), servers.size());

        // Handle Jenkins artifact download if enabled
        File downloadedArtifact = null;
        if (Boolean.TRUE.equals(task.getJenkinsEnabled())) {
            // Check if build number is provided
            String buildNumber = params != null ? params.get("jenkinsBuildNumber") : null;
            if (buildNumber == null || buildNumber.isEmpty()) {
                // Fail immediately if no build number provided
                history.setStatus("failed");
                history.setCompletedAt(LocalDateTime.now());
                history.setErrorMessage("Jenkins is enabled but no build number provided");
                history.setLogs("Jenkins enabled but missing build number, deployment aborted");
                deployHistoryMapper.insert(history);
                alertService.sendFailureAlert(history, Collections.singletonList(
                        new DeployResult("all", false, "Missing build number for Jenkins-enabled task")
                ));
                log.error("Deployment aborted: Jenkins enabled but no build number provided");
                return;
            }

            // Download artifact from Jenkins
            try {
                downloadedArtifact = jenkinsService.downloadArtifact(
                        task.getJenkinsUrl(),
                        task.getJenkinsJobName(),
                        buildNumber,
                        task.getJenkinsArtifactPath(),
                        task.getJenkinsUser(),
                        task.getJenkinsToken()
                );
                log.info("Jenkins artifact downloaded: {}", downloadedArtifact.getAbsolutePath());
                artifactService.registerArtifact(downloadedArtifact);
            } catch (RuntimeException e) {
                // Download failed, fail the whole deployment
                history.setStatus("failed");
                history.setCompletedAt(LocalDateTime.now());
                history.setErrorMessage("Failed to download artifact from Jenkins: " + e.getMessage());
                history.setLogs("Jenkins download failed: " + e.getMessage());
                deployHistoryMapper.insert(history);
                alertService.sendFailureAlert(history, Collections.singletonList(
                        new DeployResult("jenkins", false, e.getMessage())
                ));
                return;
            }
        }

        List<CompletableFuture<DeployResult>> futures = new ArrayList<>();

        // Deploy to all servers in parallel
        for (Server server : servers) {
            final File artifact = downloadedArtifact;
            CompletableFuture<DeployResult> future = CompletableFuture.supplyAsync(() -> {
                return deployToServer(history, task, server, params, artifact);
            }, executorService);
            futures.add(future);
        }

        // Wait for all deployments to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect results
        List<DeployResult> results = new ArrayList<>();
        boolean allSuccess = true;
        for (CompletableFuture<DeployResult> future : futures) {
            try {
                DeployResult result = future.get();
                results.add(result);
                if (!result.isSuccess()) {
                    allSuccess = false;
                }
            } catch (Exception e) {
                allSuccess = false;
                results.add(new DeployResult("unknown", false, e.getMessage()));
            }
        }

         // Update history (already inserted by controller with status 'running')
         history.setStatus(allSuccess ? "success" : "failed");
         history.setCompletedAt(LocalDateTime.now());

         StringBuilder logs = new StringBuilder();
         StringBuilder detailLogs = new StringBuilder();
         for (DeployResult result : results) {
             // summary 行
             logs.append(String.format("Server: %s, Success: %s, Message: %s%n",
                     result.getServerName(), result.isSuccess(), result.getMessage()));

             // 详情分段
             detailLogs.append(String.format("===== [%s %s] =====%n",
                     result.getServerName(),
                     result.getServerHost() != null ? result.getServerHost() : ""));
             if (result.getTaskLogs() != null) {
                 detailLogs.append(result.getTaskLogs());
             } else {
                 detailLogs.append("(无日志)\n");
             }
             detailLogs.append("\n");
         }
         if (downloadedArtifact != null) {
             logs.insert(0, String.format("Downloaded artifact from Jenkins: %s (%d bytes)%n",
                     downloadedArtifact.getName(), downloadedArtifact.length()));
         }
         history.setLogs(logs.toString());
         history.setDetailLogs(detailLogs.toString());

         if (!allSuccess) {
             StringBuilder errorMsg = new StringBuilder("Failed servers: ");
             for (DeployResult result : results) {
                 if (!result.isSuccess()) {
                     errorMsg.append(result.getServerName()).append(": ").append(result.getMessage()).append("; ");
                 }
             }
             history.setErrorMessage(errorMsg.toString());
         }

         deployHistoryMapper.update(history);

        // Send alert on failure
        if (!allSuccess) {
            alertService.sendFailureAlert(history, results);
        }

        log.info("Deployment {} completed with status: {}", history.getId(), history.getStatus());
    }

    private DeployResult deployToServer(DeployHistory history, Task task, Server server, Map<String, String> params, File jenkinsArtifact) {
        String serverName = server.getName();
        try {
            log.info("Deploying to server: {}", serverName);

            // If we have a Jenkins artifact, upload it to the agent
            if (jenkinsArtifact != null) {
                // Extract original filename (strip the {job}-{build}- prefix)
                String filename = jenkinsArtifact.getName();
                int lastDash = jenkinsArtifact.getName().lastIndexOf('-');
                if (lastDash >= 0 && lastDash < jenkinsArtifact.getName().length() - 1) {
                    filename = jenkinsArtifact.getName().substring(lastDash + 1);
                }

                boolean uploaded = fileTransferService.uploadFileToAgent(
                        server.getHost(), server.getPort(), server.getAgentToken(),
                        jenkinsArtifact, filename
                );

                if (!uploaded) {
                    log.error("Failed to upload artifact to agent {}", serverName);
                    DeployResult fail = new DeployResult(serverName, false, "Failed to upload artifact to agent");
                    fail.setServerHost(server.getHost());
                    return fail;
                }

                // Add artifact filename to params for deploy step
                if (params == null) {
                    params = new HashMap<>();
                }
                params.put("artifactFilename", filename);
            }

            // Parse task steps
            List<Map<String, Object>> steps = objectMapper.readValue(
                    task.getStepsDefinition(), List.class);

            // Prepare task execution request
            Map<String, Object> request = new HashMap<>();
            request.put("task_name", task.getName());
            request.put("steps", steps);
            request.put("params", params != null ? params : new HashMap<>());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + server.getAgentToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String url = String.format("http://%s:%d/api/task/execute", server.getHost(), server.getPort());
            log.info("[Deploy] POST {} task={} steps={} params={}", url, task.getName(), steps.size(), request.get("params"));
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String taskId = (String) response.getBody().get("task_id");

                // Poll for task completion
                DeployResult result = pollTaskStatus(server, taskId, serverName);
                result.setServerHost(server.getHost());
                return result;
            }

            DeployResult fail = new DeployResult(serverName, false, "Failed to start task on agent");
            fail.setServerHost(server.getHost());
            return fail;

        } catch (Exception e) {
            log.error("Failed to deploy to server {}: {}", serverName, e.getMessage());
            DeployResult fail = new DeployResult(serverName, false, e.getMessage());
            fail.setServerHost(server.getHost());
            return fail;
        }
    }

    private DeployResult pollTaskStatus(Server server, String taskId, String serverName) {
        String url = String.format("http://%s:%d/api/task/%s/status",
                server.getHost(), server.getPort(), taskId);
        log.info("[Deploy] Polling task status url={} taskId={}", url, taskId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + server.getAgentToken());

        int maxAttempts = 600; // 10 minutes with 1 second intervals
        for (int i = 0; i < maxAttempts; i++) {
            try {
                Thread.sleep(1000);

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> body = response.getBody();
                    String status = (String) body.get("status");

                    if ("success".equals(status)) {
                        DeployResult result = new DeployResult(serverName, true, "Deployment successful");
                        result.setTaskLogs(fetchTaskLogs(server, taskId));
                        return result;
                    } else if ("failed".equals(status)) {
                        String error = (String) body.getOrDefault("error", "Unknown error");
                        DeployResult result = new DeployResult(serverName, false, error);
                        result.setTaskLogs(fetchTaskLogs(server, taskId));
                        return result;
                    } else if ("cancelled".equals(status)) {
                        DeployResult result = new DeployResult(serverName, false, "Deployment cancelled");
                        result.setTaskLogs(fetchTaskLogs(server, taskId));
                        return result;
                    }
                    // Still running, continue polling
                }
            } catch (Exception e) {
                log.warn("Error polling task status: {}", e.getMessage());
            }
        }

        DeployResult timeoutResult = new DeployResult(serverName, false, "Deployment timed out");
        timeoutResult.setTaskLogs(fetchTaskLogs(server, taskId));
        return timeoutResult;
    }

    /**
     * 拉取 agent 的 per-task 日志，解析 ndjson 格式化为可读文本。
     * 404（agent 版本过低）-> 占位符；其他异常 -> 占位符。
     */
    private String fetchTaskLogs(Server server, String taskId) {
        String url = String.format("http://%s:%d/api/task/%s/logs",
                server.getHost(), server.getPort(), taskId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + server.getAgentToken());
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                return "[agent 版本过低，无日志]";
            }
            if (response.getBody() == null || response.getBody().isEmpty()) {
                return "(无日志记录)";
            }
            return formatNdjsonLogs(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return "[agent 版本过低，无日志]";
        } catch (Exception e) {
            return "[拉取失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 解析 agent 返回的 ndjson（每行一个 JSON slog record），格式化为可读文本。
     * 解析失败时 fallback 返回原始文本。
     */
    @SuppressWarnings("unchecked")
    private String formatNdjsonLogs(String ndjson) {
        StringBuilder sb = new StringBuilder();
        String[] lines = ndjson.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            try {
                Map<String, Object> rec = objectMapper.readValue(line, Map.class);
                String event = (String) rec.getOrDefault("event", "");
                String level = (String) rec.getOrDefault("level", "");
                String msg = (String) rec.getOrDefault("msg", "");
                String time = (String) rec.getOrDefault("time", "");

                switch (event) {
                    case "task.start":
                        sb.append(String.format("[%s] task start  task_name=%s step_count=%s%n",
                                time, rec.get("task_name"), rec.get("step_count")));
                        break;
                    case "task.end":
                        sb.append(String.format("[%s] task end  status=%s duration_ms=%s",
                                time, rec.get("status"), rec.get("duration_ms")));
                        if (rec.containsKey("error")) {
                            sb.append(" error=").append(rec.get("error"));
                        }
                        sb.append("\n");
                        break;
                    case "task.step.start":
                        sb.append(String.format("[%s] step[%s] %s  type=%s",
                                time, rec.getOrDefault("step_index", "?"), rec.get("step_name"), rec.get("step_type")));
                        if (rec.containsKey("command")) sb.append(" cmd=\"").append(rec.get("command")).append("\"");
                        if (rec.containsKey("deploy_path")) sb.append(" dest=").append(rec.get("deploy_path"));
                        sb.append("\n");
                        break;
                    case "task.step.end":
                        sb.append(String.format("[%s] step[%s] %s  exit=%s status=%s duration_ms=%s%n",
                                time, rec.getOrDefault("step_index", "?"), rec.get("step_name"),
                                rec.get("exit_code"), rec.get("status"), rec.get("duration_ms")));
                        Object output = rec.get("output");
                        if (output != null && !output.toString().isEmpty()) {
                            for (String outLine : output.toString().split("\n")) {
                                sb.append("  ").append(outLine).append("\n");
                            }
                        }
                        if (rec.containsKey("error")) {
                            sb.append("  error: ").append(rec.get("error")).append("\n");
                        }
                        break;
                    default:
                        sb.append(String.format("[%s] %s %s%n", time, level, msg));
                }
            } catch (Exception e) {
                // 解析失败 fallback 存原始行
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static class DeployResult {
        private String serverName;
        private String serverHost;
        private boolean success;
        private String message;
        private String taskLogs;

        public DeployResult(String serverName, boolean success, String message) {
            this.serverName = serverName;
            this.success = success;
            this.message = message;
        }

        public String getServerName() {
            return serverName;
        }

        public String getServerHost() {
            return serverHost;
        }

        public void setServerHost(String serverHost) {
            this.serverHost = serverHost;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getTaskLogs() {
            return taskLogs;
        }

        public void setTaskLogs(String taskLogs) {
            this.taskLogs = taskLogs;
        }
    }
}
