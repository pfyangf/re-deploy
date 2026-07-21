package com.redeploy.service;

import com.redeploy.model.Agent;
import com.redeploy.model.Server;
import com.redeploy.repository.AgentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AgentService {

    @Autowired
    private AgentMapper agentMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean testConnection(Server server) {
        try {
            String url = String.format("http://%s:%d/api/health", server.getHost(), server.getPort());
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    public Agent registerAgent(Long serverId, String hostname, String ip, Integer port, String token) {
        Optional<Agent> existing = agentMapper.findByHostnameAndIp(hostname, ip);
        Agent agent;
        if (existing.isPresent()) {
            agent = existing.get();
            agent.setPort(port);
            agent.setToken(token);
            agent.setServerId(serverId);
            agent.setStatus("online");
            agent.setLastHeartbeat(LocalDateTime.now());
            agentMapper.update(agent);
        } else {
            agent = new Agent();
            agent.setServerId(serverId);
            agent.setHostname(hostname);
            agent.setIp(ip);
            agent.setPort(port);
            agent.setToken(token);
            agent.setStatus("online");
            agent.setLastHeartbeat(LocalDateTime.now());
            agentMapper.insert(agent);
        }
        return agent;
    }

    public Agent updateHeartbeat(String hostname, String ip) {
        Optional<Agent> existing = agentMapper.findByHostnameAndIp(hostname, ip);
        if (existing.isPresent()) {
            Agent agent = existing.get();
            agent.setStatus("online");
            agent.setLastHeartbeat(LocalDateTime.now());
            agentMapper.update(agent);
            return agent;
        }
        return null;
    }

    public List<Agent> getAllAgents() {
        return agentMapper.findAll();
    }

    public Optional<Agent> getAgent(Long id) {
        return agentMapper.findById(id);
    }

    public void markOfflineAgents() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(90);
        List<Agent> offlineAgents = agentMapper.findByLastHeartbeatBefore(threshold);
        for (Agent agent : offlineAgents) {
            agent.setStatus("offline");
            agentMapper.update(agent);
        }
    }

    public Map<String, Object> executeCommand(Server server, String command) {
        String baseUrl = String.format("http://%s:%d", server.getHost(), server.getPort());
        String executeUrl = baseUrl + "/api/task/execute";

        Map<String, Object> request = new HashMap<>();
        request.put("task_name", "debug");
        Map<String, Object> step = new HashMap<>();
        step.put("name", "debug");
        step.put("type", "shell");
        step.put("command", command);
        step.put("timeout", 60);
        request.put("steps", List.of(step));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(server.getAgentToken());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        Map<String, Object> result = new HashMap<>();

        try {
            // 提交任务
            ResponseEntity<Map> response = restTemplate.postForEntity(executeUrl, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                result.put("success", false);
                result.put("error", "Request failed with status: " + response.getStatusCode());
                return result;
            }

            String taskId = (String) response.getBody().get("task_id");
            if (taskId == null) {
                result.put("success", false);
                result.put("error", "No task_id returned from agent");
                return result;
            }

            // 轮询任务状态直到完成（最多等待 65 秒，对应 step.timeout=60 + 5 秒缓冲）
            String statusUrl = baseUrl + "/api/task/" + taskId + "/status";
            HttpHeaders statusHeaders = new HttpHeaders();
            statusHeaders.setBearerAuth(server.getAgentToken());
            HttpEntity<Void> statusEntity = new HttpEntity<>(statusHeaders);

            long deadline = System.currentTimeMillis() + 65000L;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(500);
                ResponseEntity<Map> statusResp = restTemplate.exchange(
                        statusUrl, HttpMethod.GET, statusEntity, Map.class
                );
                if (!statusResp.getStatusCode().is2xxSuccessful() || statusResp.getBody() == null) {
                    continue;
                }
                Map<String, Object> statusBody = statusResp.getBody();
                String status = (String) statusBody.get("status");
                if ("running".equals(status)) {
                    continue;
                }

                // 任务结束，提取 step 结果
                result.put("success", "success".equals(status));
                Object error = statusBody.get("error");
                if (error != null) {
                    result.put("error", error);
                }
                Object stepsObj = statusBody.get("steps");
                if (stepsObj instanceof List) {
                    List<?> steps = (List<?>) stepsObj;
                    if (!steps.isEmpty() && steps.get(0) instanceof Map) {
                        Map<String, Object> stepResult = (Map<String, Object>) steps.get(0);
                        Object output = stepResult.get("output");
                        Object exitCode = stepResult.get("exit_code");
                        if (output != null) result.put("output", output);
                        if (exitCode != null) result.put("exitCode", exitCode);
                    }
                }
                return result;
            }

            // 超时
            result.put("success", false);
            result.put("error", "Task timed out");
            return result;

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
