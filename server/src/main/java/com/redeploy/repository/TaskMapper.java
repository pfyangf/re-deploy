package com.redeploy.repository;

import com.redeploy.model.Task;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Optional;

@Mapper
public interface TaskMapper {

    @Select("SELECT * FROM tasks ORDER BY id")
    List<Task> findAll();

    @Select("SELECT * FROM tasks WHERE id = #{id}")
    Optional<Task> findById(Long id);

    @Select("SELECT * FROM tasks WHERE group_id = #{groupId}")
    List<Task> findByGroupId(Long groupId);

    @Insert("INSERT INTO tasks (name, description, task_type, group_id, deploy_path, before_command, after_command, steps_definition, " +
            "jenkins_enabled, jenkins_url, jenkins_job_name, jenkins_artifact_path, jenkins_user, jenkins_token, created_at, updated_at) " +
            "VALUES (#{name}, #{description}, #{taskType}, #{groupId}, #{deployPath}, #{beforeCommand}, #{afterCommand}, #{stepsDefinition}, " +
            "#{jenkinsEnabled}, #{jenkinsUrl}, #{jenkinsJobName}, #{jenkinsArtifactPath}, #{jenkinsUser}, #{jenkinsToken}, datetime('now'), datetime('now'))")
    @SelectKey(statement = "SELECT last_insert_rowid()", keyProperty = "id", before = false, resultType = Long.class)
    int insert(Task task);

    @Update("UPDATE tasks SET name=#{name}, description=#{description}, task_type=#{taskType}, group_id=#{groupId}, " +
            "deploy_path=#{deployPath}, before_command=#{beforeCommand}, after_command=#{afterCommand}, steps_definition=#{stepsDefinition}, " +
            "jenkins_enabled=#{jenkinsEnabled}, jenkins_url=#{jenkinsUrl}, jenkins_job_name=#{jenkinsJobName}, " +
            "jenkins_artifact_path=#{jenkinsArtifactPath}, jenkins_user=#{jenkinsUser}, jenkins_token=#{jenkinsToken}, " +
            "updated_at=datetime('now') WHERE id=#{id}")
    int update(Task task);

    @Delete("DELETE FROM tasks WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM tasks WHERE id = #{id}")
    boolean existsById(Long id);
}
