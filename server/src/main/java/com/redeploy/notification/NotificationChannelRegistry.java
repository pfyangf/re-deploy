package com.redeploy.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.redeploy.model.NotificationConfig;
import com.redeploy.notification.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按 channelType 选 channel 实现的简单注册表。
 * 全部 channel 实现都以 Spring 组件形式注入，启动期一次性放表。
 */
@Component
public class NotificationChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelRegistry.class);

    private final Map<ChannelType, NotificationChannel> channels = new EnumMap<>(ChannelType.class);

    public NotificationChannelRegistry(List<NotificationChannel> channelBeans) {
        for (NotificationChannel c : channelBeans) {
            ChannelType type = ChannelType.fromTypeName(c.type());
            if (type == null) {
                log.warn("未知 channel type 跳过注册: {}", c.type());
                continue;
            }
            channels.put(type, c);
        }
        log.info("通知通道注册完成: {}", channels.keySet());
    }

    public NotificationChannel get(ChannelType type) {
        NotificationChannel c = channels.get(type);
        if (c == null) {
            throw new IllegalStateException("通道未注册: " + type);
        }
        return c;
    }

    public NotificationChannel getByConfig(NotificationConfig config) {
        ChannelType type = ChannelType.fromTypeName(config.getChannelType());
        if (type == null) {
            throw new IllegalStateException("未知的 channel_type: " + config.getChannelType());
        }
        return get(type);
    }

    /** 解析 config.channel_config JSON 字符串为 JsonNode，null 时返回空对象 */
    public static JsonNode parseConfig(NotificationConfig config, com.fasterxml.jackson.databind.ObjectMapper om) {
        if (config.getChannelConfig() == null || config.getChannelConfig().isEmpty()) {
            return om.createObjectNode();
        }
        try {
            return om.readTree(config.getChannelConfig());
        } catch (Exception e) {
            throw new IllegalStateException("channel_config JSON 解析失败: " + e.getMessage(), e);
        }
    }

    public enum ChannelType {
        DINGTALK("dingtalk"),
        WECHAT_WORK("wechat_work"),
        WEBHOOK("webhook"),
        EMAIL("email");

        private final String typeName;

        ChannelType(String typeName) {
            this.typeName = typeName;
        }

        public String typeName() {
            return typeName;
        }

        public static ChannelType fromTypeName(String name) {
            if (name == null) return null;
            for (ChannelType t : values()) {
                if (Objects.equals(t.typeName, name)) return t;
            }
            return null;
        }
    }
}
