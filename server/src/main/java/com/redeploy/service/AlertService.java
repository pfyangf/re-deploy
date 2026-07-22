package com.redeploy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redeploy.model.DeployHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${redeploy.dingtalk.enabled:false}")
    private boolean dingtalkEnabled;

    @Value("${redeploy.dingtalk.webhook-url:}")
    private String webhookUrl;

    @Value("${redeploy.dingtalk.notify-mode:failure-only}")
    private String notifyMode;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendFailureAlert(DeployHistory history, List<DeployService.DeployResult> results) {
        if (!dingtalkEnabled) {
            log.debug("DingTalk notifications disabled");
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("## 部署失败告警\n\n");
        content.append("- **任务ID**: ").append(history.getId()).append("\n");
        content.append("- **任务名称**: ").append(history.getTaskId()).append("\n");
        content.append("- **版本**: ").append(history.getVersion()).append("\n");
        content.append("- **开始时间**: ").append(history.getStartedAt().format(formatter)).append("\n");
        content.append("- **失败原因**: ").append(history.getErrorMessage()).append("\n\n");
        content.append("### 服务器详情\n");

        for (DeployService.DeployResult result : results) {
            String status = result.isSuccess() ? "✓ 成功" : "✗ 失败";
            content.append("- **").append(result.getServerName()).append("**: ").append(status);
            if (!result.isSuccess()) {
                content.append(" - ").append(result.getMessage());
            }
            content.append("\n");
        }

        sendDingTalkMessage("部署失败告警", content.toString());
    }

    public void sendSuccessAlert(DeployHistory history) {
        if (!dingtalkEnabled || "failure-only".equals(notifyMode)) {
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("## 部署成功通知\n\n");
        content.append("- **任务ID**: ").append(history.getId()).append("\n");
        content.append("- **版本**: ").append(history.getVersion()).append("\n");
        content.append("- **完成时间**: ").append(history.getCompletedAt().format(formatter)).append("\n");

        sendDingTalkMessage("部署成功通知", content.toString());
    }

    private void sendDingTalkMessage(String title, String content) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("msgtype", "markdown");

            Map<String, String> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", content);
            message.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);

            // Retry up to 3 times
            for (int i = 0; i < 3; i++) {
                try {
                    log.info("[Alert] POST {} (attempt {}/3)", webhookUrl, i + 1);
                    ResponseEntity<String> response = restTemplate.exchange(
                            webhookUrl, HttpMethod.POST, entity, String.class);

                    if (response.getStatusCode() == HttpStatus.OK) {
                        log.info("DingTalk notification sent successfully");
                        return;
                    }
                    log.warn("DingTalk notification failed with status: {}", response.getStatusCode());
                } catch (Exception e) {
                    log.warn("DingTalk notification attempt {} failed: {}", i + 1, e.getMessage());
                }

                // Wait before retry
                if (i < 2) {
                    Thread.sleep(1000 * (i + 1));
                }
            }

            log.error("Failed to send DingTalk notification after 3 attempts");

        } catch (Exception e) {
            log.error("Failed to send DingTalk notification", e);
        }
    }
}
