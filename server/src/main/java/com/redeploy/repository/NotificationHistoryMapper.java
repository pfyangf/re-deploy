package com.redeploy.repository;

import com.redeploy.model.NotificationHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationHistoryMapper {

    @Insert("INSERT INTO notification_history (config_id, event_type, server_id, task_id, " +
            "deploy_history_id, channel_type, target, payload, status, error_message, sent_at) " +
            "VALUES (#{configId}, #{eventType}, #{serverId}, #{taskId}, #{deployHistoryId}, " +
            "#{channelType}, #{target}, #{payload}, #{status}, #{errorMessage}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NotificationHistory history);

    @Select("SELECT * FROM notification_history WHERE id = #{id}")
    NotificationHistory findById(Long id);

    @Select("SELECT * FROM notification_history " +
            "WHERE (#{configId} IS NULL OR config_id = #{configId}) " +
            "  AND (#{eventType} IS NULL OR event_type = #{eventType}) " +
            "  AND (#{status} IS NULL OR status = #{status}) " +
            "ORDER BY sent_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<NotificationHistory> findByFilter(@Param("configId") Long configId,
                                           @Param("eventType") String eventType,
                                           @Param("status") String status,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM notification_history " +
            "WHERE (#{configId} IS NULL OR config_id = #{configId}) " +
            "  AND (#{eventType} IS NULL OR event_type = #{eventType}) " +
            "  AND (#{status} IS NULL OR status = #{status})")
    long countByFilter(@Param("configId") Long configId,
                       @Param("eventType") String eventType,
                       @Param("status") String status);

    @Delete("DELETE FROM notification_history WHERE sent_at < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int deleteOlderThan(@Param("days") int days);

    @Delete("DELETE FROM notification_history WHERE sent_at < #{cutoff}")
    int deleteBefore(@Param("cutoff") java.time.LocalDateTime cutoff);

    @Select("SELECT * FROM notification_history ORDER BY id DESC LIMIT 1")
    NotificationHistory findLatest();
}