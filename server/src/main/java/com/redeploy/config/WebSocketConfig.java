package com.redeploy.config;

import com.redeploy.repository.ServerMapper;
import com.redeploy.service.SshSessionManager;
import com.redeploy.websocket.BastionWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ServerMapper serverMapper;
    private final SshSessionManager sshSessionManager;

    public WebSocketConfig(ServerMapper serverMapper, SshSessionManager sshSessionManager) {
        this.serverMapper = serverMapper;
        this.sshSessionManager = sshSessionManager;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new BastionWebSocketHandler(serverMapper, sshSessionManager), "/ws/bastion/{serverId}")
                .setAllowedOrigins("*");
    }
}
