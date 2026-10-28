package com.redeploy.service;

import com.redeploy.model.ApiKey;
import com.redeploy.model.ApiKeyCall;
import com.redeploy.repository.ApiKeyCallMapper;
import com.redeploy.repository.ApiKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApiKeyCallService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyCallService.class);
    private static final int RETENTION_DAYS = 30;
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("MM-dd HH:00");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");

    private final ApiKeyCallMapper callMapper;
    private final ApiKeyMapper apiKeyMapper;

    public ApiKeyCallService(ApiKeyCallMapper callMapper, ApiKeyMapper apiKeyMapper) {
        this.callMapper = callMapper;
        this.apiKeyMapper = apiKeyMapper;
    }

    public void record(Long apiKeyId, String keyName, String method, String uri, int status) {
        try {
            String path = pathOf(uri);
            ApiKey owner = apiKeyId == null ? null : apiKeyMapper.findById(apiKeyId);
            ApiKeyCall call = new ApiKeyCall();
            call.setApiKeyId(apiKeyId);
            if (owner != null) {
                call.setUserId(owner.getUserId());
                call.setUsername(limit(owner.getOwnerUsername(), 64));
            }
            call.setKeyName(limit(keyName == null || keyName.isBlank() ? "未识别密钥" : keyName, 64));
            call.setMethod(limit(method == null ? "" : method, 8));
            call.setPath(path);
            call.setFeature(feature(path));
            call.setStatusCode(status);
            callMapper.insert(call);
        } catch (Exception e) {
            log.warn("记录 API 密钥调用失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> stats(String unit, int span) {
        boolean hourly = "hour".equalsIgnoreCase(unit);
        int safeSpan = hourly ? clamp(span, 1, 168) : clamp(span, 1, 30);
        LocalDateTime since = hourly
                ? LocalDateTime.now().minusHours(safeSpan - 1L).truncatedTo(ChronoUnit.HOURS)
                : LocalDateTime.now().minusDays(safeSpan - 1L).truncatedTo(ChronoUnit.DAYS);

        Map<String, Object> summary = callMapper.summary(since);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unit", hourly ? "hour" : "day");
        body.put("span", safeSpan);
        body.put("total", num(summary, "total"));
        body.put("success", num(summary, "successCount"));
        body.put("denied", num(summary, "deniedCount"));
        body.put("byUser", rows(callMapper.countByUser(since)));
        body.put("byKey", rows(callMapper.countByKey(since)));
        body.put("byFeature", rows(callMapper.countByFeature(since)));
        body.put("timeline", timeline(since, hourly, safeSpan));
        body.put("recent", callMapper.recent().stream().map(this::recentView).toList());
        return body;
    }

    @Scheduled(cron = "0 40 3 * * ?")
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = callMapper.deleteBefore(cutoff);
        if (deleted > 0) {
            log.info("API 密钥调用记录清理完成: 删除 {} 条（保留 {} 天）", deleted, RETENTION_DAYS);
        }
    }

    private List<Map<String, Object>> timeline(LocalDateTime since, boolean hourly, int span) {
        String pattern = hourly ? "%m-%d %H:00" : "%m-%d";
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : callMapper.countByTime(since, pattern)) {
            counts.put(text(row, "label"), num(row, "total"));
        }
        List<Map<String, Object>> points = new ArrayList<>();
        LocalDateTime cursor = since;
        DateTimeFormatter formatter = hourly ? HOUR : DAY;
        for (int i = 0; i < span; i++) {
            String label = cursor.format(formatter);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", label);
            point.put("total", counts.getOrDefault(label, 0L));
            points.add(point);
            cursor = hourly ? cursor.plusHours(1) : cursor.plusDays(1);
        }
        return points;
    }

    private Map<String, Object> recentView(ApiKeyCall call) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", call.getId());
        view.put("keyName", call.getKeyName());
        view.put("username", call.getUsername() == null || call.getUsername().isBlank() ? "未绑定用户" : call.getUsername());
        view.put("method", call.getMethod());
        view.put("path", call.getPath());
        view.put("feature", call.getFeature());
        view.put("statusCode", call.getStatusCode());
        view.put("createdAt", call.getCreatedAt());
        return view;
    }

    private static List<Map<String, Object>> rows(List<Map<String, Object>> raw) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (raw == null) return list;
        for (Map<String, Object> row : raw) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", text(row, "label"));
            item.put("total", num(row, "total"));
            list.add(item);
        }
        return list;
    }

    static String feature(String path) {
        if (path.startsWith("/api/downloads") || path.matches("/api/servers/\\d+/download.*")) return "远端下载";
        if (path.startsWith("/api/groups")) return "分组";
        if (path.startsWith("/api/servers")) return "服务器";
        if (path.startsWith("/api/tasks")) return "任务";
        if (path.startsWith("/api/artifacts")) return "制品";
        if (path.startsWith("/api/deploy")) return "部署";
        if (path.startsWith("/api/notification")) return "通知";
        if (path.startsWith("/api/agents")) return "代理";
        if (path.startsWith("/api/auth")) return "身份";
        return "其他";
    }

    private static String pathOf(String uri) {
        if (uri == null) return "";
        int query = uri.indexOf('?');
        String path = query >= 0 ? uri.substring(0, query) : uri;
        return limit(path, 240);
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long num(Map<String, Object> row, String key) {
        if (row == null) return 0;
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase());
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static String text(Map<String, Object> row, String key) {
        if (row == null) return "";
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase());
        return value == null ? "" : String.valueOf(value);
    }
}
