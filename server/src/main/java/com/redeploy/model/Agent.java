package com.redeploy.model;

import java.time.LocalDateTime;

public class Agent {

    private Long id;
    private Long serverId;
    private String hostname;
    private String ip;
    private Integer port = 9009;
    private String token;
    private String status = "offline";
    private LocalDateTime lastHeartbeat;
    private LocalDateTime createdAt;

    public Agent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
