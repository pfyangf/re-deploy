package com.redeploy.config;

import com.redeploy.util.SshEncryptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

@Slf4j
@Configuration
public class SshEncryptionConfig {

    @Value("${redeploy.ssh-encryption-key:}")
    private String encryptionKey;

    /**
     * 未通过环境变量/配置文件指定密钥时，自动生成的密钥持久化到该文件，
     * 保证容器重启、升级后数据库中已加密的 SSH 凭据仍可解密。
     */
    @Value("${redeploy.ssh-key-file:./data/secret/ssh-encryption.key}")
    private String keyFilePath;

    private SshEncryptionUtils encryptionUtils;

    @PostConstruct
    public void init() {
        if (encryptionKey == null || encryptionKey.trim().isEmpty()) {
            // 1. 优先从持久化文件加载（历史启动时自动生成并保存的密钥）
            String persisted = loadPersistedKey();
            if (persisted != null && !persisted.isEmpty()) {
                encryptionKey = persisted;
                log.info("SSH 加密密钥已从持久化文件加载: {}", keyFilePath);
            } else {
                // 2. 首次启动：生成随机密钥并落盘，避免重启后密钥漂移导致凭据无法解密
                encryptionKey = SshEncryptionUtils.generateRandomKey();
                persistKey(encryptionKey);
                log.warn("\n" +
                        "============================================================\n" +
                        "  未配置 redeploy.ssh-encryption-key（环境变量 REDEPLOY_SSH_ENCRYPTION_KEY）\n" +
                        "  已自动生成随机密钥并持久化到文件: {}\n" +
                        "  该文件已随数据卷持久化，容器重启/升级后会自动复用，请勿删除。\n" +
                        "  如需跨实例/跨主机迁移，请显式配置环境变量：\n" +
                        "  REDEPLOY_SSH_ENCRYPTION_KEY={}\n" +
                        "  注意：密钥一旦丢失，数据库中已保存的 SSH 密码/私钥将无法解密，\n" +
                        "  需要在「服务器管理」中重新填写并保存。\n" +
                        "============================================================",
                        keyFilePath, encryptionKey);
            }
        } else {
            encryptionKey = encryptionKey.trim();
            log.info("SSH 加密密钥来自配置项 redeploy.ssh-encryption-key（环境变量 REDEPLOY_SSH_ENCRYPTION_KEY）");
        }

        // 校验密钥长度
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encryptionKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "SSH 加密密钥配置非法：redeploy.ssh-encryption-key 必须是 Base64 编码字符串（16/24/32 字节对应 AES-128/192/256）", e);
        }
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            log.warn("SSH 加密密钥长度应为 16/24/32 字节（Base64 编码），当前为 {} 字节，AES 初始化可能失败", decoded.length);
        }
        encryptionUtils = new SshEncryptionUtils(encryptionKey);
    }

    private String loadPersistedKey() {
        try {
            Path path = Paths.get(keyFilePath);
            if (!Files.exists(path)) {
                return null;
            }
            String key = Files.readString(path, StandardCharsets.UTF_8).trim();
            return key.isEmpty() ? null : key;
        } catch (IOException e) {
            throw new IllegalStateException("读取 SSH 加密密钥持久化文件失败: " + keyFilePath, e);
        }
    }

    private void persistKey(String key) {
        Path path = Paths.get(keyFilePath);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, key + System.lineSeparator(), StandardCharsets.UTF_8);
            // 尽量收紧权限为仅所有者可读写（POSIX 环境生效，Windows 下静默跳过）
            try {
                Set<PosixFilePermission> perms = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(path, perms);
            } catch (UnsupportedOperationException ignored) {
                // Windows 等非 POSIX 文件系统不支持，忽略
            }
        } catch (IOException e) {
            // 落盘失败必须快速失败：否则重启后密钥漂移，所有已加密凭据将永久无法解密
            throw new IllegalStateException(
                    "SSH 加密密钥无法持久化到文件: " + path.toAbsolutePath()
                            + "，请检查数据目录权限，或显式配置 REDEPLOY_SSH_ENCRYPTION_KEY 环境变量", e);
        }
    }

    @Bean
    public SshEncryptionUtils sshEncryptionUtils() {
        return encryptionUtils;
    }
}
