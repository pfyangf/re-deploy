package com.redeploy.model;

import java.time.LocalDateTime;

public class Server {

    private Long id;
    private String name;
    private String host;
    private Integer port = 9009;
    private String agentToken;
    private String groupName;
    private Long groupId;
    private String description;
    private String sshUsername;
    private String sshPassword;
    private String sshPrivateKey;
    private Integer sshPort = 22;
    private String status = "offline";
    private LocalDateTime lastHeartbeat;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Server() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getAgentToken() { return agentToken; }
    public void setAgentToken(String agentToken) { this.agentToken = agentToken; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSshUsername() { return sshUsername; }
    public void setSshUsername(String sshUsername) { this.sshUsername = sshUsername; }

    public String getSshPassword() { return sshPassword; }
    public void setSshPassword(String sshPassword) { this.sshPassword = sshPassword; }

    public String getSshPrivateKey() { return sshPrivateKey; }
    public void setSshPrivateKey(String sshPrivateKey) { this.sshPrivateKey = sshPrivateKey; }

    public Integer getSshPort() { return sshPort; }
    public void setSshPort(Integer sshPort) { this.sshPort = sshPort; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
