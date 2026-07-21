package com.redeploy.websocket;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import com.redeploy.model.Server;
import com.redeploy.repository.ServerMapper;
import com.redeploy.service.SshSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class BastionWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_CHANNEL = "channel";
    private static final String ATTR_OUTPUT = "ssh-output";

    private final ServerMapper serverMapper;
    private final SshSessionManager sshSessionManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public BastionWebSocketHandler(ServerMapper serverMapper, SshSessionManager sshSessionManager) {
        this.serverMapper = serverMapper;
        this.sshSessionManager = sshSessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath();
        Long serverId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));

        Server server = serverMapper.findById(serverId).orElse(null);
        if (server == null || !hasSshConfig(server)) {
            session.close();
            return;
        }

        // Connect SSH
        Session sshSession = sshSessionManager.connect(server);
        sshSessionManager.registerSession(session.getId(), sshSession);

        // Open shell channel
        ChannelShell channel = (ChannelShell) sshSession.openChannel("shell");
        channel.setPtyType("xterm-256color");
        session.getAttributes().put(ATTR_CHANNEL, channel);

        // Start reading from SSH and sending to WebSocket
        InputStream in = channel.getInputStream();
        byte[] buffer = new byte[1024];
        CompletableFuture.runAsync(() -> {
            try {
                int read;
                while ((read = in.read(buffer)) != -1 && session.isOpen()) {
                    byte[] data = new byte[read];
                    System.arraycopy(buffer, 0, data, 0, read);
                    synchronized (session) {
                        session.sendMessage(new BinaryMessage(data));
                    }
                }
            } catch (IOException e) {
                // Connection closed normally
                try {
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (IOException ex) {
                    // Ignore
                }
            }
        }, executor);

        OutputStream out = channel.getOutputStream();
        session.getAttributes().put(ATTR_OUTPUT, out);

        channel.connect();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        // Control message format: "resize:<cols>:<rows>"
        if (payload.startsWith("resize:")) {
            String[] parts = payload.split(":");
            if (parts.length == 3) {
                try {
                    int cols = Integer.parseInt(parts[1]);
                    int rows = Integer.parseInt(parts[2]);
                    Channel channel = (Channel) session.getAttributes().get(ATTR_CHANNEL);
                    if (channel instanceof ChannelShell) {
                        // ChannelShell inherits setPtySize from ChannelSession (public method)
                        ((ChannelShell) channel).setPtySize(cols, rows, 0, 0);
                    }
                } catch (NumberFormatException e) {
                    // Ignore malformed resize messages
                }
            }
            return;
        }

        // Normal text message is terminal input (from xterm.js onData)
        OutputStream out = (OutputStream) session.getAttributes().get(ATTR_OUTPUT);
        if (out != null) {
            try {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                log.error("Failed to write text input to SSH", e);
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // Binary message is terminal input
        OutputStream out = (OutputStream) session.getAttributes().get(ATTR_OUTPUT);
        if (out != null) {
            try {
                out.write(message.getPayload().array());
                out.flush();
            } catch (IOException e) {
                log.error("handleBinaryMessage", e);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Channel channel = (Channel) session.getAttributes().get(ATTR_CHANNEL);
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        sshSessionManager.closeSession(session.getId());
        super.afterConnectionClosed(session, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sshSessionManager.closeSession(session.getId());
        if (session.isOpen()) {
            session.close();
        }
        super.handleTransportError(session, exception);
    }

    private boolean hasSshConfig(Server server) {
        return server.getSshUsername() != null && !server.getSshUsername().isEmpty()
                && ((server.getSshPassword() != null && !server.getSshPassword().isEmpty())
                || (server.getSshPrivateKey() != null && !server.getSshPrivateKey().isEmpty()));
    }
}
