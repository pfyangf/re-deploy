package com.redeploy.controller;

import com.redeploy.model.Group;
import com.redeploy.model.Server;
import com.redeploy.repository.GroupMapper;
import com.redeploy.repository.ServerMapper;
import com.redeploy.service.AgentService;
import com.redeploy.util.SshEncryptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    @Autowired
    private ServerMapper serverMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private AgentService agentService;

    @Autowired(required = false)
    private SshEncryptionUtils sshEncryptionUtils;

    @GetMapping
    public List<Server> listServers(@RequestParam(required = false) Long groupId) {
        List<Server> list = (groupId != null)
                ? serverMapper.findByGroupId(groupId)
                : serverMapper.findAll();
        list.forEach(this::stripSensitive);
        return list;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Server> getServer(@PathVariable Long id) {
        return serverMapper.findById(id)
                .map(server -> {
                    stripSensitive(server);
                    return ResponseEntity.ok(server);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 脱敏：SSH 密码/私钥密文不下发前端，仅返回是否已配置的标志位。
     */
    private void stripSensitive(Server server) {
        server.setHasSshPassword(server.getSshPassword() != null && !server.getSshPassword().isEmpty());
        server.setHasSshPrivateKey(server.getSshPrivateKey() != null && !server.getSshPrivateKey().isEmpty());
        server.setSshPassword(null);
        server.setSshPrivateKey(null);
    }

    /**
     * 根据 groupId 回填 group_name 冗余列（前端只传 groupId），避免 group_name 被写成 null。
     */
    private void fillGroupName(Server server) {
        if (server.getGroupId() != null) {
            groupMapper.findById(server.getGroupId())
                    .ifPresent(g -> server.setGroupName(g.getName()));
        }
    }

    @PostMapping
    public Server createServer(@RequestBody Server server) {
        // Assign to default group if not specified
        if (server.getGroupId() == null) {
            Group defaultGroup = groupMapper.findByName("default").orElse(null);
            if (defaultGroup != null) {
                server.setGroupId(defaultGroup.getId());
            }
        }
        fillGroupName(server);
        // Encrypt sensitive fields if they are provided and encryption is enabled
        if (sshEncryptionUtils != null) {
            if (server.getSshPassword() != null && !server.getSshPassword().isEmpty()) {
                server.setSshPassword(sshEncryptionUtils.encrypt(server.getSshPassword()));
            }
            if (server.getSshPrivateKey() != null && !server.getSshPrivateKey().isEmpty()) {
                server.setSshPrivateKey(sshEncryptionUtils.encrypt(server.getSshPrivateKey()));
            }
        }
        serverMapper.insert(server);
        stripSensitive(server);
        return server;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Server> updateServer(@PathVariable Long id, @RequestBody Server server) {
        return serverMapper.findById(id)
                .map(existing -> {
                    existing.setName(server.getName());
                    existing.setHost(server.getHost());
                    existing.setPort(server.getPort());
                    existing.setAgentToken(server.getAgentToken());
                    existing.setGroupId(server.getGroupId());
                    // 根据 groupId 回填 group_name（前端只传 groupId，直接取 server.groupName 会是 null）
                    fillGroupName(existing);
                    existing.setDescription(server.getDescription());
                    existing.setSshUsername(server.getSshUsername());
                    existing.setSshPort(server.getSshPort());
                    // 密码/私钥：前端留空表示不修改，保留库中原有密文；填写了新值才加密覆盖
                    if (sshEncryptionUtils != null) {
                        if (server.getSshPassword() != null && !server.getSshPassword().isEmpty()) {
                            existing.setSshPassword(sshEncryptionUtils.encrypt(server.getSshPassword()));
                        }
                        if (server.getSshPrivateKey() != null && !server.getSshPrivateKey().isEmpty()) {
                            existing.setSshPrivateKey(sshEncryptionUtils.encrypt(server.getSshPrivateKey()));
                        }
                    }
                    serverMapper.update(existing);
                    stripSensitive(existing);
                    return ResponseEntity.ok(existing);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServer(@PathVariable Long id) {
        if (serverMapper.existsById(id)) {
            serverMapper.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnectivity(@PathVariable Long id) {
        return serverMapper.findById(id)
                .map(server -> {
                    boolean connected = agentService.testConnection(server);
                    // Update server status based on connection test result
                    server.setStatus(connected ? "online" : "offline");
                    serverMapper.update(server);
                    Map<String, Object> result = Map.of(
                            "serverId", id,
                            "connected", connected,
                            "host", server.getHost(),
                            "port", server.getPort()
                    );
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Debug command execution - execute arbitrary shell command via Agent
    @PostMapping("/{id}/debug/exec")
    public ResponseEntity<Map<String, Object>> debugExec(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return serverMapper.findById(id)
                .map(server -> {
                    String command = body.get("command");
                    if (command == null || command.isEmpty()) {
                        Map<String, Object> error = new HashMap<>();
                        error.put("error", "command is required");
                        return ResponseEntity.badRequest().<Map<String, Object>>body(error);
                    }

                    try {
                        Map<String, Object> result = agentService.executeCommand(server, command);
                        return ResponseEntity.ok(result);
                    } catch (Exception e) {
                        Map<String, Object> error = new HashMap<>();
                        error.put("error", e.getMessage());
                        return ResponseEntity.badRequest().body(error);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
