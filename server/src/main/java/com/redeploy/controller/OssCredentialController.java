package com.redeploy.controller;

import com.redeploy.model.OssCredential;
import com.redeploy.repository.OssCredentialMapper;
import com.redeploy.repository.TaskMapper;
import com.redeploy.service.OssArtifactService;
import com.redeploy.service.OssLocations;
import com.redeploy.util.SshEncryptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/oss-credentials")
public class OssCredentialController {

    private final OssCredentialMapper credentialMapper;
    private final TaskMapper taskMapper;
    private final SshEncryptionUtils encryptionUtils;
    private final OssArtifactService ossArtifactService;

    public OssCredentialController(OssCredentialMapper credentialMapper,
                                   TaskMapper taskMapper,
                                   SshEncryptionUtils encryptionUtils,
                                   OssArtifactService ossArtifactService) {
        this.credentialMapper = credentialMapper;
        this.taskMapper = taskMapper;
        this.encryptionUtils = encryptionUtils;
        this.ossArtifactService = ossArtifactService;
    }

    @GetMapping
    public List<OssCredential> list() {
        List<OssCredential> credentials = credentialMapper.findAll();
        credentials.forEach(this::stripSecret);
        return credentials;
    }

    @GetMapping("/{id}")
    public ResponseEntity<OssCredential> get(@PathVariable Long id) {
        return credentialMapper.findById(id)
                .map(credential -> {
                    stripSecret(credential);
                    return ResponseEntity.ok(credential);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/test")
    public ResponseEntity<?> test(@RequestBody OssCredential credential) {
        try {
            String secret = credential.getAccessKeySecret();
            if ((secret == null || secret.isBlank()) && credential.getId() != null) {
                OssCredential stored = credentialMapper.findById(credential.getId()).orElse(null);
                if (stored == null) {
                    return badRequest("OSS 凭证不存在");
                }
                try {
                    secret = encryptionUtils.decrypt(stored.getAccessKeySecret());
                } catch (RuntimeException e) {
                    return badRequest("OSS 凭证无法解密，请重新填写 AccessKey Secret");
                }
                if (credential.getEndpoint() == null || credential.getEndpoint().isBlank()) {
                    credential.setEndpoint(stored.getEndpoint());
                }
                if (credential.getAccessKeyId() == null || credential.getAccessKeyId().isBlank()) {
                    credential.setAccessKeyId(stored.getAccessKeyId());
                }
            }
            String message = ossArtifactService.testConnection(
                    credential.getEndpoint(), credential.getAccessKeyId(), secret);
            return ResponseEntity.ok(Map.of("ok", true, "message", message));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody OssCredential credential) {
        try {
            prepare(credential, null);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
        credential.setId(null);
        credential.setAccessKeySecret(encryptionUtils.encrypt(credential.getAccessKeySecret()));
        credentialMapper.insert(credential);
        stripSecret(credential);
        return ResponseEntity.ok(credential);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody OssCredential credential) {
        OssCredential existing = credentialMapper.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            prepare(credential, existing);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
        existing.setName(credential.getName());
        existing.setEndpoint(credential.getEndpoint());
        existing.setAccessKeyId(credential.getAccessKeyId());
        if (credential.getAccessKeySecret() != null && !credential.getAccessKeySecret().isBlank()) {
            existing.setAccessKeySecret(encryptionUtils.encrypt(credential.getAccessKeySecret()));
        }
        credentialMapper.update(existing);
        stripSecret(existing);
        return ResponseEntity.ok(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (credentialMapper.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (taskMapper.countByOssCredentialId(id) > 0) {
            return badRequest("仍有任务使用该凭证，无法删除");
        }
        credentialMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void prepare(OssCredential credential, OssCredential existing) {
        if (credential.getName() == null || credential.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("凭证名称不能为空");
        }
        String name = credential.getName().trim();
        if (name.length() > 100) {
            throw new IllegalArgumentException("凭证名称过长");
        }
        var sameName = credentialMapper.findByName(name);
        if (sameName.isPresent() && (existing == null || !sameName.get().getId().equals(existing.getId()))) {
            throw new IllegalArgumentException("凭证名称已存在");
        }
        credential.setName(name);
        credential.setEndpoint(OssLocations.endpoint(credential.getEndpoint()));
        if (credential.getAccessKeyId() == null || credential.getAccessKeyId().trim().isEmpty()) {
            throw new IllegalArgumentException("AccessKey ID 不能为空");
        }
        String accessKeyId = credential.getAccessKeyId().trim();
        if (accessKeyId.length() > 128) {
            throw new IllegalArgumentException("AccessKey ID 过长");
        }
        credential.setAccessKeyId(accessKeyId);
        String secret = credential.getAccessKeySecret();
        if (existing == null && (secret == null || secret.isBlank())) {
            throw new IllegalArgumentException("请填写 AccessKey Secret");
        }
        if (secret != null && secret.length() > 256) {
            throw new IllegalArgumentException("AccessKey Secret 过长");
        }
    }

    private void stripSecret(OssCredential credential) {
        credential.setHasSecret(credential.getAccessKeySecret() != null && !credential.getAccessKeySecret().isEmpty());
        credential.setAccessKeySecret(null);
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return ResponseEntity.badRequest().body(error);
    }
}
