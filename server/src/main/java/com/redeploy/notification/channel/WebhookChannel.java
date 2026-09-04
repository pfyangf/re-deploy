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
 * 通用 Webhook 通道 — 把消息以 JSON POST 到用户指定 URL。
 *
 * channel_config JSON:
 * {
 *   "url": "https://example.com/webhook",
 *   "headers": { "Authorization": "Bearer ..." }    // 可选
 * }
 */
@Component
public class WebhookChannel implements NotificationChannel {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String type() {
        return "webhook";
    }

    @Override
    public NotificationResult send(NotificationMessage message, NotificationConfig config) {
        JsonNode cfg = NotificationJson.read(config.getChannelConfig());
        String url = cfg.path("url").asText("");
        if (url.isEmpty()) {
            return NotificationResult.fail("missing url in channel_config");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", message.getTitle());
        payload.put("text", message.getText());
        payload.put("markdown", message.getMarkdown());
        payload.put("html", message.getHtml());
        payload.put("subject", message.getSubject());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            JsonNode hdrs = cfg.path("headers");
            if (hdrs.isObject()) {
                hdrs.fields().forEachRemaining(e ->
                        headers.set(e.getKey(), e.getValue().asText("")));
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return NotificationResult.ok();
            }
            return NotificationResult.fail("HTTP " + response.getStatusCode());
        } catch (Exception e) {
            return NotificationResult.fail("send error: " + e.getMessage());
        }
    }

    @Override
    public String extractTargetFromConfig(JsonNode channelConfig) {
        String url = channelConfig.path("url").asText("");
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return "invalid-url";
        }
    }
}