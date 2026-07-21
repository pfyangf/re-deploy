package com.redeploy.repository;

import com.redeploy.model.Agent;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AgentMapper {

    @Select("SELECT * FROM agents ORDER BY id")
    List<Agent> findAll();

    @Select("SELECT * FROM agents WHERE id = #{id}")
    Optional<Agent> findById(Long id);

    @Select("SELECT * FROM agents WHERE hostname = #{hostname} AND ip = #{ip}")
    Optional<Agent> findByHostnameAndIp(@Param("hostname") String hostname, @Param("ip") String ip);

    @Select("SELECT * FROM agents WHERE status = #{status}")
    List<Agent> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM agents WHERE server_id = #{serverId}")
    List<Agent> findByServerId(@Param("serverId") Long serverId);

    @Select("SELECT * FROM agents WHERE last_heartbeat < #{date}")
    List<Agent> findByLastHeartbeatBefore(@Param("date") LocalDateTime date);

    @Insert("INSERT INTO agents (server_id, hostname, ip, port, token, status, last_heartbeat, created_at) " +
            "VALUES (#{serverId}, #{hostname}, #{ip}, #{port}, #{token}, #{status}, #{lastHeartbeat}, datetime('now'))")
    @SelectKey(statement = "SELECT last_insert_rowid()", keyProperty = "id", before = false, resultType = Long.class)
    int insert(Agent agent);

    @Update("UPDATE agents SET server_id=#{serverId}, port=#{port}, token=#{token}, " +
            "status=#{status}, last_heartbeat=#{lastHeartbeat} WHERE id=#{id}")
    int update(Agent agent);

    @Delete("DELETE FROM agents WHERE id = #{id}")
    int deleteById(Long id);
}
