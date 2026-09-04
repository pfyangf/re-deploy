package com.redeploy.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.redeploy.model.NotificationConfig;
import com.redeploy.model.NotificationHistory;
import com.redeploy.model.SystemEvent;
import com.redeploy.repository.NotificationConfigMapper;
import com.redeploy.repository.NotificationHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 核心调度器：
 *  1. 收 SystemEvent（Spring 事件）
 *  2. 从 DB 查所有启用的 NotificationConfig
 *  3. 按 event_type / server_id / task_template_id 三维度匹配
 *  4. 命中后投递给对应 channel，写入 notification_history
 *  5. 失败重试 3 次（指数退避），全部失败才标 status=failed
 *
 * 注意：{@link Async} 必须加在被 Spring 代理直接调用的 {@link EventListener} 方法上，
 * 不能加在被同类自调用的内部方法上（自调用绕过代理，@Async 不生效）。
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int MAX_RETRY = 3;

    private final NotificationConfigMapper configMapper;
    private final NotificationHistoryMapper historyMapper;
    private final NotificationChannelRegistry registry;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    public NotificationDispatcher(NotificationConfigMapper configMapper,
                                  NotificationHistoryMapper historyMapper,
                                  NotificationChannelRegistry registry,
                                  com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.historyMapper = historyMapper;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 事件入口：异步执行（走 notificationExecutor 线程池），不阻塞发布事件的部署线程。
     */
    @Async("notificationExecutor")
    @EventListener
    public void onEvent(SystemEvent event) {
        try {
            dispatch(event);
        } catch (Exception e) {
            log.error("事件分发失败: type={}, taskId={}", event.getType(), event.getTaskId(), e);
        }
    }

    /** 同步分发逻辑（由 onEvent 在线程池里调用，也可供测试直接调用）。 */
    public void dispatch(SystemEvent event) {
        // 一次事件可能命中多条 config；逐条投递互不影响
        List<NotificationConfig> candidates = configMapper.findEnabledByEventType(event.getType());
        if (candidates == null || candidates.isEmpty()) {
            log.debug("事件 {} 无订阅配置", event.getType());
            return;
        }

        for (NotificationConfig cfg : candidates) {
            if (!matches(cfg, event)) continue;

            try {
                deliver(cfg, event);
            } catch (Exception e) {
                log.error("投递通知失败 configId={}, eventType={}", cfg.getId(), event.getType(), e);
            }
        }
    }

    private boolean matches(NotificationConfig cfg, SystemEvent event) {
        // event_type 已在 SQL 过滤，这里只校验 server / task_template 维度
        if (hasIds(cfg.getServerGroupIds())) {
            Set<Long> allowed = parseIds(cfg.getServerGroupIds());
            if (event.getServerIds() != null && !event.getServerIds().isEmpty() &&
                    event.getServerIds().stream().noneMatch(allowed::contains)) {
                return false;
            }
        }
        if (hasIds(cfg.getTaskTemplateIds())) {
            Set<Long> allowed = parseIds(cfg.getTaskTemplateIds());
            Long tid = event.getTask() != null ? event.getTask().getId() : event.getTaskId();
            if (tid == null || !allowed.contains(tid)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasIds(String json) {
        if (json == null || json.isBlank()) return false;
        return !"[]".equals(json.trim());
    }

    private Set<Long> parseIds(String json) {
        try {
            return new HashSet<>(objectMapper.readValue(json, new TypeReference<List<Long>>() {}));
        } catch (Exception e) {
            log.warn("解析 ID 列表失败: {}", json);
            return Set.of();
        }
    }

    private void deliver(NotificationConfig cfg, SystemEvent event) {
        NotificationMessage message = NotificationRenderer.render(event);
        NotificationChannel channel = registry.getByConfig(cfg);

        // 重试 3 次，指数退避
        NotificationResult result = null;
        Exception lastError = null;
        for (int i = 1; i <= MAX_RETRY; i++) {
            try {
                result = channel.send(message, cfg);
                if (result.isSuccess()) {
                    break;
                }
                lastError = new RuntimeException(result.getErrorMessage());
                log.warn("通知发送失败 configId={} attempt={}/{} err={}",
                        cfg.getId(), i, MAX_RETRY, result.getErrorMessage());
            } catch (Exception e) {
                lastError = e;
                log.warn("通知发送异常 configId={} attempt={}/{}", cfg.getId(), i, MAX_RETRY, e);
            }
            try {
                if (i < MAX_RETRY) Thread.sleep(1000L * i);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        boolean success = result != null && result.isSuccess();

        // 写历史
        try {
            NotificationHistory h = new NotificationHistory();
            h.setConfigId(cfg.getId());
            h.setEventType(event.getType());
            h.setChannelType(cfg.getChannelType());
            h.setDeployHistoryId(event.getHistory() != null ? event.getHistory().getId() : null);
            h.setTaskId(event.getTask() != null ? event.getTask().getId() : event.getTaskId());
            try {
                h.setTarget(channel.extractTarget(cfg));
            } catch (Exception ex) {
                log.debug("抽取通知 target 失败 configId={}", cfg.getId(), ex);
            }
            String body = message.getMarkdown() != null ? message.getMarkdown() : message.getText();
            h.setPayload(truncate(body, 2000));
            h.setStatus(success ? "success" : "failed");
            if (!success) {
                h.setErrorMessage(lastError != null ? truncate(lastError.getMessage(), 500) : "unknown");
            }
            h.setSentAt(LocalDateTime.now());
            historyMapper.insert(h);
        } catch (Exception e) {
            log.error("写通知历史失败", e);
        }
    }

    /** 测试发送：不走历史、不重试、立即返回结果。 */
    public NotificationResult testSend(Long configId, String customBody) {
        NotificationConfig cfg = configMapper.findById(configId);
        if (cfg == null) throw new IllegalArgumentException("配置不存在: " + configId);

        String body = (customBody != null && !customBody.isBlank())
                ? customBody
                : "这是一条来自 ReDeploy 通知管理页的测试消息。";
        NotificationMessage msg = new NotificationMessage("测试通知", body);

        NotificationChannel channel = registry.getByConfig(cfg);
        return channel.send(msg, cfg);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
