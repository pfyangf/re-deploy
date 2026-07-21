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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Async
    public void deploy(DeployHistory history, Task task, List<Server> servers, Map<String, String> params) {
        log.info("Starting deployment {} for task '{}' to {} servers",
                history.getId(), task.getName(), servers.size());

        List<CompletableFuture<DeployResult>> futures = new ArrayList<>();

        // Deploy to all servers in parallel
        for (Server server : servers) {
            CompletableFuture<DeployResult> future = CompletableFuture.supplyAsync(() -> {
                return deployToServer(history, task, server, params);
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

        // Update history
        history.setStatus(allSuccess ? "success" : "failed");
        history.setCompletedAt(LocalDateTime.now());

        StringBuilder logs = new StringBuilder();
        for (DeployResult result : results) {
            logs.append(String.format("Server: %s, Success: %s, Message: %s\n",
                    result.getServerName(), result.isSuccess(), result.getMessage()));
        }
        history.setLogs(logs.toString());

        if (!allSuccess) {
            StringBuilder errorMsg = new StringBuilder("Failed servers: ");
            for (DeployResult result : results) {
                if (!result.isSuccess()) {
                    errorMsg.append(result.getServerName()).append(": ").append(result.getMessage()).append("; ");
                }
            }
            history.setErrorMessage(errorMsg.toString());
        }

        deployHistoryMapper.insert(history);

        // Send alert on failure
        if (!allSuccess) {
            alertService.sendFailureAlert(history, results);
        }

        log.info("Deployment {} completed with status: {}", history.getId(), history.getStatus());
    }

    private DeployResult deployToServer(DeployHistory history, Task task, Server server, Map<String, String> params) {
        String serverName = server.getName();
        try {
            log.info("Deploying to server: {}", serverName);

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
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String taskId = (String) response.getBody().get("task_id");

                // Poll for task completion
                return pollTaskStatus(server, taskId, serverName);
            }

            return new DeployResult(serverName, false, "Failed to start task on agent");

        } catch (Exception e) {
            log.error("Failed to deploy to server {}: {}", serverName, e.getMessage());
            return new DeployResult(serverName, false, e.getMessage());
        }
    }

    private DeployResult pollTaskStatus(Server server, String taskId, String serverName) {
        String url = String.format("http://%s:%d/api/task/%s/status",
                server.getHost(), server.getPort(), taskId);

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
                        return new DeployResult(serverName, true, "Deployment successful");
                    } else if ("failed".equals(status)) {
                        String error = (String) body.getOrDefault("error", "Unknown error");
                        return new DeployResult(serverName, false, error);
                    } else if ("cancelled".equals(status)) {
                        return new DeployResult(serverName, false, "Deployment cancelled");
                    }
                    // Still running, continue polling
                }
            } catch (Exception e) {
                log.warn("Error polling task status: {}", e.getMessage());
            }
        }

        return new DeployResult(serverName, false, "Deployment timed out");
    }

    public static class DeployResult {
        private String serverName;
        private boolean success;
        private String message;

        public DeployResult(String serverName, boolean success, String message) {
            this.serverName = serverName;
            this.success = success;
            this.message = message;
        }

        public String getServerName() {
            return serverName;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
