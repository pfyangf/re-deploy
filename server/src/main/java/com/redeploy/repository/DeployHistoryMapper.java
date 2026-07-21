package com.redeploy.repository;

import com.redeploy.model.DeployHistory;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface DeployHistoryMapper {

    @Select("SELECT * FROM deploy_history ORDER BY created_at DESC")
    List<DeployHistory> findAllOrderByCreatedAtDesc();

    @Select("SELECT * FROM deploy_history WHERE id = #{id}")
    Optional<DeployHistory> findById(Long id);

    @Select("SELECT * FROM deploy_history WHERE status = #{status}")
    List<DeployHistory> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM deploy_history WHERE task_id = #{taskId}")
    List<DeployHistory> findByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT * FROM deploy_history WHERE created_at < #{date}")
    List<DeployHistory> findByCreatedAtBefore(@Param("date") LocalDateTime date);

    @Insert("INSERT INTO deploy_history (task_id, server_ids, version, status, started_at, completed_at, error_message, logs, created_at) " +
            "VALUES (#{taskId}, #{serverIds}, #{version}, #{status}, #{startedAt}, #{completedAt}, #{errorMessage}, #{logs}, datetime('now'))")
    @SelectKey(statement = "SELECT last_insert_rowid()", keyProperty = "id", before = false, resultType = Long.class)
    int insert(DeployHistory history);

    @Update("UPDATE deploy_history SET status=#{status}, completed_at=#{completedAt}, " +
            "error_message=#{errorMessage}, logs=#{logs} WHERE id=#{id}")
    int update(DeployHistory history);

    @Delete("DELETE FROM deploy_history WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM deploy_history WHERE created_at < #{date}")
    int deleteByCreatedAtBefore(@Param("date") LocalDateTime date);
}
