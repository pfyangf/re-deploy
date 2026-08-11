package com.redeploy.repository;

import com.redeploy.model.Server;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ServerMapper {

    @Select("SELECT * FROM servers ORDER BY id")
    List<Server> findAll();

    @Select("SELECT * FROM servers WHERE id = #{id}")
    Optional<Server> findById(Long id);

    @Select("SELECT * FROM servers WHERE host = #{host} AND port = #{port}")
    Optional<Server> findByHostAndPort(@Param("host") String host, @Param("port") Integer port);

    @Select("SELECT * FROM servers WHERE group_name = #{groupName}")
    List<Server> findByGroupName(@Param("groupName") String groupName);

    @Select("SELECT * FROM servers WHERE group_id = #{groupId}")
    List<Server> findByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT * FROM servers WHERE status = #{status}")
    List<Server> findByStatus(@Param("status") String status);

    @Insert("INSERT INTO servers (name, host, port, agent_token, group_name, group_id, description, " +
            "ssh_username, ssh_password, ssh_private_key, ssh_port, status, created_at, updated_at) " +
            "VALUES (#{name}, #{host}, #{port}, #{agentToken}, #{groupName}, #{groupId}, #{description}, " +
            "#{sshUsername}, #{sshPassword}, #{sshPrivateKey}, #{sshPort}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Server server);

    @Update("UPDATE servers SET name=#{name}, host=#{host}, port=#{port}, agent_token=#{agentToken}, " +
            "group_name=#{groupName}, group_id=#{groupId}, description=#{description}, " +
            "ssh_username=#{sshUsername}, ssh_password=#{sshPassword}, ssh_private_key=#{sshPrivateKey}, ssh_port=#{sshPort}, " +
            "status=#{status}, last_heartbeat=#{lastHeartbeat}, updated_at=NOW() WHERE id=#{id}")
    int update(Server server);

    @Delete("DELETE FROM servers WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM servers WHERE id = #{id}")
    boolean existsById(Long id);

    @Select("SELECT COUNT(*) FROM servers WHERE group_id = #{groupId}")
    long countByGroupId(Long groupId);
}
