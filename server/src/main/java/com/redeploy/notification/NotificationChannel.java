package com.redeploy.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.redeploy.model.NotificationConfig;

/**
 * 通知通道抽象 — 每种通道（钉钉/企微/Webhook/邮件）实现一个。
 * type() 返回通道标识；send() 把消息渲染后投递。
 */
public interface NotificationChannel {

    /** 通道类型标识，对应 notification_config.channel_type */
    String type();

    /**
     * 发送通知。
     *
     * @param message 渲染好的消息内容（通道无关的中间表示）
     * @param config  通知配置，channel_config 是 JSON，按通道约定解析
     * @return 发送结果
     */
    NotificationResult send(NotificationMessage message, NotificationConfig config);

    /** 通道目标摘要（脱敏），写入 notification_history.target */
    default String extractTarget(NotificationConfig config) {
        JsonNode cfg = NotificationJson.read(config.getChannelConfig());
        return extractTargetFromConfig(cfg);
    }

    /** 子类覆盖：从 channel_config 抽取脱敏后的目标描述 */
    String extractTargetFromConfig(JsonNode channelConfig);
}