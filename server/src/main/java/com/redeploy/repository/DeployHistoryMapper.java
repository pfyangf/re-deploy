package com.redeploy.repository;

import com.redeploy.model.DeployHistory;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface DeployHistoryMapper {

    // 列表查询排除 detail_logs 大字段
    @Select("SELECT id, task_id, server_ids, version, status, started_at, completed_at, error_message, logs, created_at FROM deploy_history ORDER BY created_at DESC")
    List<DeployHistory> findAllOrderByCreatedAtDesc();

    @Select("SELECT id, task_id, server_ids, version, status, started_at, completed_at, error_message, logs, created_at FROM deploy_history WHERE id = #{id}")
    Optional<DeployHistory> findById(Long id);

    @Select("SELECT id, task_id, server_ids, version, status, started_at, completed_at, error_message, logs, created_at FROM deploy_history WHERE status = #{status}")
    List<DeployHistory> findByStatus(@Param("status") String status);

    @Select("SELECT id, task_id, server_ids, version, status, started_at, completed_at, error_message, logs, created_at FROM deploy_history WHERE task_id = #{taskId}")
    List<DeployHistory> findByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT id, task_id, server_ids, version, status, started_at, completed_at, error_message, logs, created_at FROM deploy_history WHERE created_at < #{date}")
    List<DeployHistory> findByCreatedAtBefore(@Param("date") LocalDateTime date);

    // 详情查询含 detail_logs
    @Select("SELECT * FROM deploy_history WHERE id = #{id}")
    Optional<DeployHistory> findByIdWithDetail(Long id);

    @Insert("INSERT INTO deploy_history (task_id, server_ids, version, status, started_at, completed_at, error_message, logs, created_at) " +
            "VALUES (#{taskId}, #{serverIds}, #{version}, #{status}, #{startedAt}, #{completedAt}, #{errorMessage}, #{logs}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DeployHistory history);

    @Update("UPDATE deploy_history SET status=#{status}, completed_at=#{completedAt}, " +
            "error_message=#{errorMessage}, logs=#{logs}, detail_logs=#{detailLogs} WHERE id=#{id}")
    int update(DeployHistory history);

    @Delete("DELETE FROM deploy_history WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM deploy_history WHERE created_at < #{date}")
    int deleteByCreatedAtBefore(@Param("date") LocalDateTime date);
}
