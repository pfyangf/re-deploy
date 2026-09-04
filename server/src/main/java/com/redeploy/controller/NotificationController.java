package com.redeploy.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redeploy.model.NotificationConfig;
import com.redeploy.model.NotificationHistory;
import com.redeploy.notification.NotificationChannelRegistry;
import com.redeploy.notification.NotificationDispatcher;
import com.redeploy.repository.NotificationConfigMapper;
import com.redeploy.repository.NotificationHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通知管理 REST API：
 *  - GET    /api/notification/channels           通道类型清单
 *  - GET    /api/notification/configs            配置列表
 *  - POST   /api/notification/configs            新建
 *  - PUT    /api/notification/configs/{id}       更新
 *  - DELETE /api/notification/configs/{id}       删除
 *  - POST   /api/notification/configs/{id}/test  测试发送
 *  - GET    /api/notification/history            历史分页查询
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @Autowired private NotificationConfigMapper configMapper;
    @Autowired private NotificationHistoryMapper historyMapper;
    @Autowired private NotificationDispatcher dispatcher;
    @Autowired private ObjectMapper objectMapper;

    @GetMapping("/channels")
    public List<Map<String, String>> channels() {
        return List.of(
                Map.of("type", "dingtalk", "name", "钉钉机器人", "icon", "dingtalk"),
                Map.of("type", "wechat_work", "name", "企业微信机器人", "icon", "wechat"),
                Map.of("type", "webhook", "name", "自定义 Webhook", "icon", "webhook"),
                Map.of("type", "email", "name", "邮件 SMTP", "icon", "email")
        );
    }

    @GetMapping("/event-types")
    public List<Map<String, String>> eventTypes() {
        return List.of(
                Map.of("type", "TASK_STARTED", "name", "任务开始"),
                Map.of("type", "TASK_SUCCESS", "name", "任务成功"),
                Map.of("type", "TASK_FAILED", "name", "任务失败"),
                Map.of("type", "TASK_CANCELLED", "name", "任务取消"),
                Map.of("type", "STEP_FAILED", "name", "步骤失败"),
                Map.of("type", "STEP_TIMEOUT", "name", "步骤超时"),
                Map.of("type", "AGENT_ONLINE", "name", "Agent 上线"),
                Map.of("type", "AGENT_OFFLINE", "name", "Agent 离线")
        );
    }

    @GetMapping("/configs")
    public List<NotificationConfig> list() {
        return configMapper.findAll();
    }

    @GetMapping("/configs/{id}")
    public ResponseEntity<NotificationConfig> get(@PathVariable Long id) {
        NotificationConfig cfg = configMapper.findById(id);
        return cfg == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(cfg);
    }

    @PostMapping("/configs")
    public ResponseEntity<NotificationConfig> create(@RequestBody NotificationConfig req) throws Exception {
        validate(req);
        req.setId(null);
        configMapper.insert(req);
        return ResponseEntity.ok(req);
    }

    @PutMapping("/configs/{id}")
    public ResponseEntity<NotificationConfig> update(@PathVariable Long id, @RequestBody NotificationConfig req) throws Exception {
        NotificationConfig existing = configMapper.findById(id);
        if (existing == null) return ResponseEntity.notFound().build();
        validate(req);
        req.setId(id);
        configMapper.update(req);
        return ResponseEntity.ok(req);
    }

    @DeleteMapping("/configs/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        configMapper.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/configs/{id}/test")
    public ResponseEntity<Map<String, Object>> test(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, String> body) {
        String customBody = body != null ? body.get("body") : null;
        try {
            var result = dispatcher.testSend(id, customBody);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", result.isSuccess());
            resp.put("message", result.isSuccess() ? "ok" : result.getErrorMessage());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.warn("测试发送失败 configId={}", id, e);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam(required = false) Long configId,
                                        @RequestParam(required = false) String eventType,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "50") int limit,
                                        @RequestParam(defaultValue = "0") int offset) {
        if (limit > 500) limit = 500;
        List<NotificationHistory> rows = historyMapper.findByFilter(configId, eventType, status, limit, offset);
        long total = historyMapper.countByFilter(configId, eventType, status);
        Map<String, Object> resp = new HashMap<>();
        resp.put("rows", rows);
        resp.put("total", total);
        resp.put("limit", limit);
        resp.put("offset", offset);
        return resp;
    }

    private void validate(NotificationConfig req) throws Exception {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (req.getChannelType() == null
                || NotificationChannelRegistry.ChannelType.fromTypeName(req.getChannelType()) == null) {
            throw new IllegalArgumentException("不支持的 channel_type: " + req.getChannelType());
        }
        if (req.getChannelConfig() == null || req.getChannelConfig().isBlank()) {
            throw new IllegalArgumentException("channel_config 不能为空");
        }
        // 校验 channel_config 必须是合法 JSON
        objectMapper.readTree(req.getChannelConfig());
        // 校验 event_types JSON 数组
        if (req.getEventTypes() == null || req.getEventTypes().isBlank()) {
            throw new IllegalArgumentException("event_types 不能为空");
        }
        List<String> events = objectMapper.readValue(req.getEventTypes(), new TypeReference<List<String>>() {});
        Set<String> allowed = Set.of("TASK_STARTED", "TASK_SUCCESS", "TASK_FAILED", "TASK_CANCELLED",
                "STEP_FAILED", "STEP_TIMEOUT", "AGENT_ONLINE", "AGENT_OFFLINE");
        for (String e : events) {
            if (!allowed.contains(e)) {
                throw new IllegalArgumentException("不支持的事件类型: " + e);
            }
        }
        // enabled 默认 true
        if (req.getEnabled() == null) req.setEnabled(true);
        // 限定维度可选；选了就要是 JSON 数组字符串
        if (req.getServerGroupIds() != null && !req.getServerGroupIds().isBlank()) {
            objectMapper.readValue(req.getServerGroupIds(), new TypeReference<List<Long>>() {});
        }
        if (req.getTaskTemplateIds() != null && !req.getTaskTemplateIds().isBlank()) {
            objectMapper.readValue(req.getTaskTemplateIds(), new TypeReference<List<Long>>() {});
        }
    }
}
