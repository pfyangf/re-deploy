package com.redeploy.service;

import com.jcraft.jsch.*;
import com.redeploy.model.Server;
import com.redeploy.repository.ServerMapper;
import com.redeploy.util.SshEncryptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SshSessionManager {

    @Autowired(required = false)
    private SshEncryptionUtils sshEncryptionUtils;

    @Autowired
    private ServerMapper serverMapper;

    private final ConcurrentHashMap<String, Session> activeSessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Cleanup on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeAllSessions));
    }

    public Session connect(Server server) throws JSchException, IOException {
        JSch jsch = new JSch();
        String username = server.getSshUsername();
        int port = server.getSshPort() != null ? server.getSshPort() : 22;

        // Decrypt credentials if needed
        String password = null;
        String privateKey = null;

        if (sshEncryptionUtils != null) {
            if (server.getSshPassword() != null && !server.getSshPassword().isEmpty()) {
                password = sshEncryptionUtils.decrypt(server.getSshPassword());
            }
            if (server.getSshPrivateKey() != null && !server.getSshPrivateKey().isEmpty()) {
                privateKey = sshEncryptionUtils.decrypt(server.getSshPrivateKey());
            }
        } else {
            password = server.getSshPassword();
            privateKey = server.getSshPrivateKey();
        }

        // Add private key if provided
        if (privateKey != null && !privateKey.isEmpty()) {
            jsch.addIdentity("ssh-key", privateKey.getBytes(), null, null);
        }

        Session session = jsch.getSession(username, server.getHost(), port);

        // Disable strict host key checking for simplicity
        session.setConfig("StrictHostKeyChecking", "no");

        // Set password if provided
        if (password != null && !password.isEmpty()) {
            session.setPassword(password);
        }

        session.connect(30000);
        return session;
    }

    public void registerSession(String sessionId, Session session) {
        activeSessions.put(sessionId, session);
    }

    public void closeSession(String sessionId) {
        Session session = activeSessions.remove(sessionId);
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    public Session getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    private void closeAllSessions() {
        activeSessions.values().forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        activeSessions.clear();
    }
}
