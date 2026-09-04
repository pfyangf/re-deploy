package com.redeploy.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redeploy.model.NotificationConfig;
import com.redeploy.repository.NotificationConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动期迁移：把 application.yml 中的 redeploy.dingtalk.* 老配置自动导入 notification_config 表。
 * 触发条件：yml enabled=true + webhook-url 非空 + DB 还没有任何 dingtalk 通道的 enabled 配置。
 * 仅插入一次；后续用户在通知管理页面改 yml 不会覆盖。
 */
@Component
@Order(10)
public class NotificationConfigMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfigMigrator.class);

    @Value("${redeploy.dingtalk.enabled:false}")
    private boolean legacyEnabled;

    @Value("${redeploy.dingtalk.webhook-url:}")
    private String legacyWebhookUrl;

    @Value("${redeploy.dingtalk.secret:}")
    private String legacySecret;

    @Value("${redeploy.dingtalk.notify-mode:failure-only}")
    private String legacyNotifyMode;

    private final NotificationConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    public NotificationConfigMigrator(NotificationConfigMapper configMapper, ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        if (!legacyEnabled || legacyWebhookUrl == null || legacyWebhookUrl.isBlank()) {
            log.debug("钉钉 yml 旧配置未启用，跳过迁移");
            return;
        }

        try {
            int existing = configMapper.countByChannelType("dingtalk");
            if (existing > 0) {
                log.info("已存在 {} 条钉钉通知配置，跳过 yml 迁移", existing);
                return;
            }

            // 按 notify-mode 决定订阅事件
            String events = "failure-only".equalsIgnoreCase(legacyNotifyMode)
                    ? "[\"TASK_FAILED\"]"
                    : "[\"TASK_STARTED\",\"TASK_SUCCESS\",\"TASK_FAILED\",\"TASK_CANCELLED\"]";

            String channelConfig;
            if (legacySecret != null && !legacySecret.isBlank()) {
                channelConfig = "{\"webhookUrl\":\"" + escape(legacyWebhookUrl) +
                        "\",\"secret\":\"" + escape(legacySecret) + "\"}";
            } else {
                channelConfig = "{\"webhookUrl\":\"" + escape(legacyWebhookUrl) + "\"}";
            }

            NotificationConfig cfg = new NotificationConfig();
            cfg.setName("从 yml 迁移：钉钉告警（自动）");
            cfg.setChannelType("dingtalk");
            cfg.setChannelConfig(channelConfig);
            cfg.setEventTypes(events);
            cfg.setServerGroupIds(null);
            cfg.setTaskTemplateIds(null);
            cfg.setEnabled(true);
            cfg.setCreatedAt(LocalDateTime.now());
            cfg.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(cfg);

            log.warn("已自动从 yml 迁移钉钉告警配置到 notification_config（id={}），" +
                    "请尽快在通知管理页面校验并保存；yml 配置将在下个版本移除。", cfg.getId());
        } catch (Exception e) {
            log.error("钉钉 yml 迁移失败", e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
