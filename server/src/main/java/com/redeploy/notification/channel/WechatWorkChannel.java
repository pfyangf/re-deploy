package com.redeploy.notification.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.redeploy.model.NotificationConfig;
import com.redeploy.notification.NotificationChannel;
import com.redeploy.notification.NotificationJson;
import com.redeploy.notification.NotificationMessage;
import com.redeploy.notification.NotificationResult;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信机器人通道 — text 消息。
 *
 * channel_config JSON:
 * {
 *   "webhookUrl": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=..."
 * }
 */
@Component
public class WechatWorkChannel implements NotificationChannel {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String type() {
        // 与 NotificationChannelRegistry.ChannelType / 前端渠道值保持一致
        return "wechat_work";
    }

    @Override
    public NotificationResult send(NotificationMessage message, NotificationConfig config) {
        JsonNode cfg = NotificationJson.read(config.getChannelConfig());
        String webhookUrl = cfg.path("webhookUrl").asText("");
        if (webhookUrl.isEmpty()) {
            return NotificationResult.fail("missing webhookUrl in channel_config");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "text");
        Map<String, String> text = new LinkedHashMap<>();
        // 企业微信 text 消息内容，@all 等保留
        String content = message.getText() != null ? message.getText() : message.getMarkdown();
        if (content != null && message.getTitle() != null) {
            content = "【" + message.getTitle() + "】\n" + content;
        }
        text.put("content", content != null ? content : "");
        payload.put("text", text);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    webhookUrl, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                String body = response.getBody() == null ? "" : response.getBody();
                if (body.contains("\"errcode\":0") || body.contains("\"errmsg\":\"ok\"")) {
                    return NotificationResult.ok();
                }
                return NotificationResult.fail("wechat non-zero: " + body);
            }
            return NotificationResult.fail("HTTP " + response.getStatusCode());
        } catch (Exception e) {
            return NotificationResult.fail("send error: " + e.getMessage());
        }
    }

    @Override
    public String extractTargetFromConfig(JsonNode channelConfig) {
        String url = channelConfig.path("webhookUrl").asText("");
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return "invalid-url";
        }
    }
}