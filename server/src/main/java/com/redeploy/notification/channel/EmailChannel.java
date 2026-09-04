package com.redeploy.notification.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.redeploy.model.NotificationConfig;
import com.redeploy.notification.NotificationChannel;
import com.redeploy.notification.NotificationJson;
import com.redeploy.notification.NotificationMessage;
import com.redeploy.notification.NotificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件通道。要求 spring-boot-starter-mail 与 spring.mail.* 配置可用。
 * channel_config JSON:
 *  {
 *    "from": "noreply@example.com",     // 必填，发件人
 *    "to":  "ops@example.com,dev@example.com", // 必填，收件人逗号分隔
 *    "cc":  "",                          // 可选，抄送
 *    "subjectPrefix": "[ReDeploy]"       // 可选
 *  }
 *
 * JavaMailSender 由 Spring Boot 自动配置（spring.mail.host 等）。
 * 未配置邮件 starter 时 JavaMailSender 不注入 → EmailChannel 直接返回失败结果。
 */
@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final JavaMailSender mailSender;

    @Autowired(required = false)
    public EmailChannel(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public String type() {
        return "email";
    }

    @Override
    public NotificationResult send(NotificationMessage message, NotificationConfig config) {
        if (mailSender == null) {
            return NotificationResult.fail("邮件通道未启用（缺少 spring-boot-starter-mail 依赖或 spring.mail.* 配置）");
        }
        JsonNode cfg = NotificationJson.read(config.getChannelConfig());

        String from = textOr(cfg, "from", null);
        String to = textOr(cfg, "to", null);
        String cc = textOr(cfg, "cc", null);
        String subjectPrefix = textOr(cfg, "subjectPrefix", "");

        if (from == null || to == null) {
            return NotificationResult.fail("email 配置必须包含 from / to");
        }

        try {
            String subject = subjectPrefix + " " +
                    (message.getSubject() != null ? message.getSubject() : message.getTitle());
            String body = message.getText() != null ? message.getText() : message.getMarkdown();

            SimpleMailMessage sm = new SimpleMailMessage();
            sm.setFrom(from);
            sm.setTo(splitEmails(to));
            if (cc != null && !cc.isBlank()) {
                sm.setCc(splitEmails(cc));
            }
            sm.setSubject(subject.trim());
            sm.setText(body != null ? body : "");
            mailSender.send(sm);
            log.info("[email] 已发送: subject='{}' to={}", sm.getSubject(), to);
            return NotificationResult.ok();
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            return NotificationResult.fail("邮件发送失败: " + e.getMessage());
        }
    }

    @Override
    public String extractTargetFromConfig(JsonNode channelConfig) {
        String to = textOr(channelConfig, "to", "");
        return to == null ? "" : to;
    }

    private static String textOr(JsonNode node, String field, String defaultValue) {
        if (node == null) return defaultValue;
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? defaultValue : v.asText();
    }

    private static String[] splitEmails(String csv) {
        String[] parts = csv.split(",");
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.toArray(new String[0]);
    }
}
