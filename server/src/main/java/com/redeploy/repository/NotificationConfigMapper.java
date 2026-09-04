package com.redeploy.repository;

import com.redeploy.model.NotificationConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationConfigMapper {

    @Select("SELECT * FROM notification_config ORDER BY id DESC")
    List<NotificationConfig> findAll();

    @Select("SELECT * FROM notification_config WHERE id = #{id}")
    NotificationConfig findById(Long id);

    @Select("SELECT * FROM notification_config WHERE enabled = 1 ORDER BY id")
    List<NotificationConfig> findAllEnabled();

    /**
     * 命中查询：event_types 是 JSON 数组字符串，用 JSON_SEARCH 匹配。
     * 索引 idx_event_types 加快扫描。
     */
    @Select("SELECT * FROM notification_config " +
            "WHERE enabled = 1 AND JSON_SEARCH(event_types, 'one', #{eventType}) IS NOT NULL")
    List<NotificationConfig> findEnabledByEventType(@Param("eventType") String eventType);

    @Select("SELECT COUNT(*) FROM notification_config WHERE channel_type = #{type}")
    int countByChannelType(@Param("type") String channelType);

    @Insert("INSERT INTO notification_config (name, channel_type, channel_config, event_types, " +
            "server_group_ids, task_template_ids, enabled, created_at, updated_at) " +
            "VALUES (#{name}, #{channelType}, #{channelConfig}, #{eventTypes}, " +
            "#{serverGroupIds}, #{taskTemplateIds}, #{enabled}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NotificationConfig config);

    @Update("UPDATE notification_config SET name=#{name}, channel_type=#{channelType}, " +
            "channel_config=#{channelConfig}, event_types=#{eventTypes}, " +
            "server_group_ids=#{serverGroupIds}, task_template_ids=#{taskTemplateIds}, " +
            "enabled=#{enabled}, updated_at=NOW() WHERE id=#{id}")
    int update(NotificationConfig config);

    @Delete("DELETE FROM notification_config WHERE id = #{id}")
    int deleteById(Long id);
}