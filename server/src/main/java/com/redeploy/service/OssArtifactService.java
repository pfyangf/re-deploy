package com.redeploy.service;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ListBucketsRequest;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.redeploy.model.OssCredential;
import com.redeploy.model.Task;
import com.redeploy.repository.ArtifactMapper;
import com.redeploy.repository.OssCredentialMapper;
import com.redeploy.util.SshEncryptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 部署前从阿里云 OSS 把任务上的固定对象流式下载到本地。每次都重新下载。
 */
@Service
public class OssArtifactService {

    private static final Logger log = LoggerFactory.getLogger(OssArtifactService.class);
    private static final int MAX_RETAINED = 3;
    /** 凭证验证的整次请求上限，不重试。 */
    private static final int TEST_TIMEOUT_MS = 30_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Value("${redeploy.upload-dir:./data/uploads}")
    private String uploadDir;

    @Autowired
    private OssCredentialMapper ossCredentialMapper;

    @Autowired
    private SshEncryptionUtils sshEncryptionUtils;

    @Autowired(required = false)
    private ArtifactMapper artifactMapper;

    /**
     * 用列举 Bucket 校验 Endpoint 和 AccessKey。密钥能通过签名即视为配置正确；
     * 没有列举权限时仍返回成功说明，避免把「只能读对象」的密钥判失败。
     */
    public String testConnection(String endpoint, String accessKeyId, String secret) {
        String normalized = OssLocations.endpoint(endpoint);
        if (accessKeyId == null || accessKeyId.isBlank()) {
            throw new IllegalArgumentException("AccessKey ID 不能为空");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("请填写 AccessKey Secret");
        }
        OSS client = testClient(normalized, accessKeyId.trim(), secret);
        try {
            ListBucketsRequest request = new ListBucketsRequest();
            request.setMaxKeys(1);
            client.listBuckets(request);
            return "连接成功，凭证可用";
        } catch (OSSException e) {
            if ("AccessDenied".equals(e.getErrorCode())) {
                return "凭证可用，但没有列举 Bucket 的权限";
            }
            log.warn("OSS credential test rejected code={}", e.getErrorCode());
            throw new IllegalArgumentException(ossError(e));
        } catch (ClientException e) {
            log.warn("OSS credential test failed: {}", e.getMessage());
            if (isTimeout(e)) {
                throw new IllegalArgumentException("连接阿里云 OSS 超时（30 秒）");
            }
            throw new IllegalArgumentException("连接阿里云 OSS 失败: " + e.getMessage());
        } finally {
            client.shutdown();
        }
    }

    public File download(Task task) {
        if (task.getOssCredentialId() == null || task.getOssBucket() == null || task.getOssObjectKey() == null) {
            throw new RuntimeException("OSS 构建物配置不完整");
        }
        OssCredential credential = ossCredentialMapper.findById(task.getOssCredentialId())
                .orElseThrow(() -> new RuntimeException("OSS 凭证不存在"));
        String secret = decryptSecret(credential);
        String bucket = task.getOssBucket().trim();
        String objectKey = task.getOssObjectKey().trim();
        String filename = OssLocations.fileName(objectKey);

        try {
            Path dir = Paths.get(uploadDir, "oss", String.valueOf(task.getId() == null ? 0 : task.getId()));
            Files.createDirectories(dir);
            File output = dir.resolve(LocalDateTime.now().format(STAMP) + "-" + filename).toFile();
            log.info("OSS GET endpoint={} bucket={} key={}", credential.getEndpoint(), bucket, objectKey);
            OSS client = client(credential.getEndpoint(), credential.getAccessKeyId(), secret);
            try {
                client.getObject(new GetObjectRequest(bucket, objectKey), output);
            } catch (OSSException e) {
                deleteQuietly(output);
                log.error("OSS download rejected bucket={} key={} code={}", bucket, objectKey, e.getErrorCode());
                throw new RuntimeException(ossError(e));
            } catch (ClientException e) {
                deleteQuietly(output);
                log.error("OSS download failed bucket={} key={}", bucket, objectKey, e);
                throw new RuntimeException("连接阿里云 OSS 失败: " + e.getMessage());
            } finally {
                client.shutdown();
            }
            cleanupOldFiles(dir);
            log.info("OSS artifact downloaded: {} ({} bytes)", output.getName(), output.length());
            return output;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("OSS download failed bucket={} key={}", bucket, objectKey, e);
            throw new RuntimeException("从阿里云 OSS 下载失败: " + e.getMessage());
        }
    }

    private OSS client(String endpoint, String accessKeyId, String secret) {
        ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
        conf.setConnectionTimeout(15_000);
        conf.setSocketTimeout(60_000);
        return new OSSClientBuilder().build(endpoint, accessKeyId, secret, conf);
    }

    /** 验证只发一次请求，连接、读取和整次请求都限制在 30 秒。 */
    private OSS testClient(String endpoint, String accessKeyId, String secret) {
        ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
        conf.setMaxErrorRetry(0);
        conf.setConnectionTimeout(TEST_TIMEOUT_MS);
        conf.setSocketTimeout(TEST_TIMEOUT_MS);
        conf.setConnectionRequestTimeout(TEST_TIMEOUT_MS);
        conf.setRequestTimeoutEnabled(true);
        conf.setRequestTimeout(TEST_TIMEOUT_MS);
        return new OSSClientBuilder().build(endpoint, accessKeyId, secret, conf);
    }

    private static boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String decryptSecret(OssCredential credential) {
        try {
            return sshEncryptionUtils.decrypt(credential.getAccessKeySecret());
        } catch (RuntimeException e) {
            log.error("Failed to decrypt OSS credential {}", credential.getId());
            throw new RuntimeException("OSS 凭证无法解密，请重新保存 AccessKey Secret");
        }
    }

    private static String ossError(OSSException e) {
        String code = e.getErrorCode() == null ? "" : e.getErrorCode();
        return switch (code) {
            case "NoSuchKey" -> "OSS 对象不存在";
            case "NoSuchBucket" -> "OSS Bucket 不存在";
            case "AccessDenied" -> "OSS 凭证无权访问该对象";
            case "InvalidAccessKeyId" -> "AccessKey ID 无效";
            case "SignatureDoesNotMatch" -> "AccessKey Secret 不正确";
            default -> "从阿里云 OSS 下载失败" + (code.isEmpty() ? "" : ": " + code);
        };
    }

    private void cleanupOldFiles(Path dir) {
        try {
            File[] files = dir.toFile().listFiles(File::isFile);
            if (files == null || files.length <= MAX_RETAINED) return;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            int extra = files.length - MAX_RETAINED;
            for (int i = 0; i < extra; i++) {
                File old = files[i];
                String path = old.getAbsolutePath();
                if (!old.delete()) {
                    log.warn("Failed to delete old OSS artifact: {}", old.getName());
                    continue;
                }
                log.info("Cleaned up old OSS artifact: {}", old.getName());
                if (artifactMapper != null) {
                    try {
                        artifactMapper.deleteByFilePath(path);
                    } catch (Exception ex) {
                        log.warn("Failed to remove artifact DB record for {}: {}", path, ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error cleaning up old OSS artifacts: {}", e.getMessage());
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            log.warn("Failed to delete incomplete OSS download: {}", file.getAbsolutePath());
        }
    }
}
