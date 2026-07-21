package com.redeploy.config;

import com.redeploy.util.SshEncryptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class SshEncryptionConfig {

    @Value("${redeploy.ssh-encryption-key:}")
    private String encryptionKey;

    private SshEncryptionUtils encryptionUtils;

    @PostConstruct
    public void init() {
        if (encryptionKey == null || encryptionKey.trim().isEmpty()) {
            // Generate random key if not configured
            encryptionKey = SshEncryptionUtils.generateRandomKey();
            System.out.println("============================================================\n");
            System.out.println("  SSH encryption key was not configured in application.yml");
            System.out.println("  Generated random key: " + encryptionKey);
            System.out.println("\n  PLEASE SAVE THIS KEY - if you lose it, all stored SSH");
            System.out.println("  credentials will become unrecoverable!\n");
            System.out.println("  Add this line to your application.yml:\n");
            System.out.println("  redeploy.ssh-encryption-key: " + encryptionKey);
            System.out.println("\n============================================================");
        }
        // Validate key length
        byte[] decoded = java.util.Base64.getDecoder().decode(encryptionKey);
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            System.out.println("WARNING: SSH encryption key length should be 16/24/32 bytes (Base64 encoded), got " + decoded.length + " bytes");
        }
        encryptionUtils = new SshEncryptionUtils(encryptionKey);
    }

    @Bean
    public SshEncryptionUtils sshEncryptionUtils() {
        return encryptionUtils;
    }
}
