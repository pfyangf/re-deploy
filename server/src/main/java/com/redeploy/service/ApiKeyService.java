package com.redeploy.service;

import com.redeploy.model.ApiKey;
import com.redeploy.model.SysUser;
import com.redeploy.repository.ApiKeyMapper;
import com.redeploy.repository.RbacMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyMapper apiKeyMapper;
    private final RbacMapper rbacMapper;

    public ApiKeyService(ApiKeyMapper apiKeyMapper, RbacMapper rbacMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.rbacMapper = rbacMapper;
    }

    public List<Map<String, Object>> list() {
        return apiKeyMapper.findAll().stream().map(key -> view(key, null)).toList();
    }

    public List<Map<String, Object>> owners() {
        List<Map<String, Object>> users = new ArrayList<>();
        for (SysUser user : rbacMapper.findUsers()) {
            if (user.getEnabled() == null || user.getEnabled() == 0) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("displayName", user.getDisplayName());
            users.add(item);
        }
        return users;
    }

    public Map<String, Object> create(Map<String, Object> body) {
        String name = text(body.get("name")).trim();
        if (name.isEmpty() || name.length() > 64) {
            throw new IllegalArgumentException("名称需为 1 到 64 个字符");
        }
        Long userId = userId(body.get("userId"));
        SysUser owner = rbacMapper.findUserById(userId);
        if (owner == null || owner.getEnabled() == null || owner.getEnabled() == 0) {
            throw new IllegalArgumentException("请选择有效用户");
        }
        if (apiKeyMapper.countByUserAndName(userId, name) > 0) {
            throw new IllegalArgumentException("该用户下已有同名密钥");
        }
        Integer days = expireDays(body.get("expireDays"));
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        String token = "rdk_" + secret;

        ApiKey key = new ApiKey();
        key.setName(name);
        key.setKeyPrefix("rdk_" + secret.substring(0, 8));
        key.setTokenHash(sha256(token));
        key.setEnabled(1);
        key.setUserId(userId);
        key.setExpiresAt(days == null ? null : LocalDateTime.now().plusDays(days));
        apiKeyMapper.insert(key);
        ApiKey stored = apiKeyMapper.findById(key.getId());
        return view(stored == null ? key : stored, token);
    }

    public Map<String, Object> updateEnabled(Long id, Map<String, Object> body) {
        ApiKey key = apiKeyMapper.findById(id);
        if (key == null) throw new IllegalArgumentException("密钥不存在");
        int enabled = enabledFlag(body.get("enabled"));
        apiKeyMapper.updateEnabled(id, enabled);
        key.setEnabled(enabled);
        return view(key, null);
    }

    public void delete(Long id) {
        if (apiKeyMapper.findById(id) == null) throw new IllegalArgumentException("密钥不存在");
        apiKeyMapper.delete(id);
    }

    private Map<String, Object> view(ApiKey key, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", key.getId());
        body.put("name", key.getName());
        body.put("userId", key.getUserId());
        body.put("ownerUsername", key.getOwnerUsername());
        body.put("ownerDisplayName", key.getOwnerDisplayName());
        body.put("keyPrefix", key.getKeyPrefix());
        body.put("enabled", key.getEnabled() == null ? 0 : key.getEnabled());
        body.put("expiresAt", key.getExpiresAt());
        body.put("lastUsedAt", key.getLastUsedAt());
        body.put("createdAt", key.getCreatedAt());
        if (token != null) body.put("token", token);
        return body;
    }

    private static Long userId(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new IllegalArgumentException("请选择所属用户");
        }
        try {
            long id = Long.parseLong(String.valueOf(raw).trim());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("请选择所属用户");
        }
    }

    private static Integer expireDays(Object raw) {
        if (raw == null) return null;
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equals(text)) return null;
        int days;
        try {
            days = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("有效天数不正确");
        }
        if (days < 1 || days > 3650) {
            throw new IllegalArgumentException("有效天数需在 1 到 3650 之间");
        }
        return days;
    }

    private static int enabledFlag(Object raw) {
        if (raw instanceof Boolean flag) return flag ? 1 : 0;
        if (raw instanceof Number number) {
            int value = number.intValue();
            if (value == 0 || value == 1) return value;
        }
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text)) return 1;
        if ("0".equals(text) || "false".equalsIgnoreCase(text)) return 0;
        throw new IllegalArgumentException("启用状态不正确");
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
