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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉机器人通道 — Markdown 消息。
 *
 * channel_config JSON:
 * {
 *   "webhookUrl": "https://oapi.dingtalk.com/robot/send?access_token=...",
 *   "secret": "SEC..."     // 可选；空则不加签
 * }
 */
@Component
public class DingTalkChannel implements NotificationChannel {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String type() {
        return "dingtalk";
    }

    @Override
    public NotificationResult send(NotificationMessage message, NotificationConfig config) {
        JsonNode cfg = NotificationJson.read(config.getChannelConfig());
        String webhookUrl = cfg.path("webhookUrl").asText("");
        String secret = cfg.path("secret").asText("");

        if (webhookUrl.isEmpty()) {
            return NotificationResult.fail("missing webhookUrl in channel_config");
        }

        String finalUrl = webhookUrl;
        if (!secret.isEmpty()) {
            try {
                finalUrl = appendSignature(webhookUrl, secret);
            } catch (Exception e) {
                return NotificationResult.fail("sign failed: " + e.getMessage());
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "markdown");
        Map<String, String> markdown = new LinkedHashMap<>();
        markdown.put("title", message.getTitle() != null ? message.getTitle() : "通知");
        markdown.put("text", message.getMarkdown() != null ? message.getMarkdown() : message.getText());
        payload.put("markdown", markdown);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    finalUrl, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                String body = response.getBody() == null ? "" : response.getBody();
                if (body.contains("\"errcode\":0")) {
                    return NotificationResult.ok();
                }
                return NotificationResult.fail("dingtalk non-zero errcode: " + body);
            }
            return NotificationResult.fail("HTTP " + response.getStatusCode());
        } catch (Exception e) {
            return NotificationResult.fail("send error: " + e.getMessage());
        }
    }

    @Override
    public String extractTargetFromConfig(JsonNode channelConfig) {
        String url = channelConfig.path("webhookUrl").asText("");
        // 只留域名，避免 URL 全量泄露
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return "invalid-url";
        }
    }

    private String appendSignature(String webhookUrl, String secret) throws Exception {
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(
                HexFormat.of().formatHex(signBytes),
                "UTF-8");
        return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
    }
}