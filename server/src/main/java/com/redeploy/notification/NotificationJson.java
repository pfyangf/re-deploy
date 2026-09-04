package com.redeploy.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 共享 JSON 解析工具（带异常吞掉，返回 NullNode） */
public final class NotificationJson {

    private static final Logger log = LoggerFactory.getLogger(NotificationJson.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NotificationJson() {}

    /** 解析失败返回 NullNode，记 WARN */
    public static JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("failed to parse channel_config JSON: {}", e.getMessage());
            return MAPPER.createObjectNode();
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}