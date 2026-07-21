package com.redeploy.controller;

import com.redeploy.model.Agent;
import com.redeploy.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    @Autowired
    private AgentService agentService;

    @GetMapping
    public List<Agent> listAgents() {
        return agentService.getAllAgents();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Agent> getAgent(@PathVariable Long id) {
        return agentService.getAgent(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/register")
    public ResponseEntity<Agent> registerAgent(@RequestBody Map<String, Object> request) {
        Long serverId = Long.valueOf(request.get("serverId").toString());
        String hostname = request.get("hostname").toString();
        String ip = request.get("ip").toString();
        Integer port = Integer.valueOf(request.get("port").toString());
        String token = request.get("token").toString();

        Agent agent = agentService.registerAgent(serverId, hostname, ip, port, token);
        return ResponseEntity.ok(agent);
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Agent> heartbeat(@RequestBody Map<String, String> request) {
        String hostname = request.get("hostname");
        String ip = request.get("ip");

        Agent agent = agentService.updateHeartbeat(hostname, ip);
        if (agent != null) {
            return ResponseEntity.ok(agent);
        }
        return ResponseEntity.notFound().build();
    }
}
