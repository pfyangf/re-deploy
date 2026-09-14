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
            try {
                if (server.getSshPassword() != null && !server.getSshPassword().isEmpty()) {
                    password = sshEncryptionUtils.decrypt(server.getSshPassword());
                }
                if (server.getSshPrivateKey() != null && !server.getSshPrivateKey().isEmpty()) {
                    privateKey = sshEncryptionUtils.decrypt(server.getSshPrivateKey());
                }
            } catch (RuntimeException e) {
                // 典型场景：未配置 REDEPLOY_SSH_ENCRYPTION_KEY 时容器重启/升级导致自动密钥漂移，
                // 数据库里的旧密文无法解密（BadPaddingException）
                throw new IOException("SSH 凭据解密失败：加密密钥与保存凭据时不一致"
                        + "（常见于未配置 REDEPLOY_SSH_ENCRYPTION_KEY 且服务重启/升级后密钥重新生成）。"
                        + "请在「服务器管理」中重新编辑该服务器并填写 SSH 密码/私钥后保存；"
                        + "若仍保留首次启动时打印的密钥，可将其配置为 REDEPLOY_SSH_ENCRYPTION_KEY 环境变量后重启服务。", e);
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
