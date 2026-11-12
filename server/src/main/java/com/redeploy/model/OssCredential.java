package com.redeploy.model;

import java.time.LocalDateTime;

/**
 * 可复用的阿里云 OSS 凭证。AccessKey Secret 只以密文落库，接口不回传明文。
 */
public class OssCredential {

    private Long id;
    private String name;
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    /** 非持久化：是否已保存 Secret。密文不下发。 */
    private boolean hasSecret;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OssCredential() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }

    public boolean isHasSecret() { return hasSecret; }
    public void setHasSecret(boolean hasSecret) { this.hasSecret = hasSecret; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
