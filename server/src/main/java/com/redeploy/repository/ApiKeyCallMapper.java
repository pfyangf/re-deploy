package com.redeploy.repository;

import com.redeploy.model.ApiKeyCall;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ApiKeyCallMapper {

    @Insert("INSERT INTO api_key_call (api_key_id, user_id, username, key_name, method, path, feature, status_code, created_at) " +
            "VALUES (#{apiKeyId}, #{userId}, #{username}, #{keyName}, #{method}, #{path}, #{feature}, #{statusCode}, NOW())")
    int insert(ApiKeyCall call);

    @Select("SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(status_code < 400), 0) AS successCount, " +
            "COALESCE(SUM(status_code >= 400), 0) AS deniedCount " +
            "FROM api_key_call WHERE created_at >= #{since}")
    Map<String, Object> summary(@Param("since") LocalDateTime since);

    @Select("SELECT IFNULL(NULLIF(username, ''), '未绑定用户') AS label, COUNT(*) AS total FROM api_key_call " +
            "WHERE created_at >= #{since} GROUP BY username ORDER BY total DESC")
    List<Map<String, Object>> countByUser(@Param("since") LocalDateTime since);

    @Select("SELECT CONCAT(IFNULL(NULLIF(username, ''), '未绑定用户'), ' / ', key_name) AS label, COUNT(*) AS total " +
            "FROM api_key_call WHERE created_at >= #{since} GROUP BY username, key_name ORDER BY total DESC")
    List<Map<String, Object>> countByKey(@Param("since") LocalDateTime since);

    @Select("SELECT feature AS label, COUNT(*) AS total FROM api_key_call " +
            "WHERE created_at >= #{since} GROUP BY feature ORDER BY total DESC")
    List<Map<String, Object>> countByFeature(@Param("since") LocalDateTime since);

    @Select("SELECT DATE_FORMAT(created_at, #{pattern}) AS label, COUNT(*) AS total FROM api_key_call " +
            "WHERE created_at >= #{since} GROUP BY label ORDER BY label")
    List<Map<String, Object>> countByTime(@Param("since") LocalDateTime since, @Param("pattern") String pattern);

    @Select("SELECT COUNT(*) FROM api_key_call")
    long countAll();

    @Select("SELECT id, api_key_id, user_id, username, key_name, method, path, feature, status_code, created_at " +
            "FROM api_key_call ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ApiKeyCall> recentPage(@Param("offset") int offset, @Param("limit") int limit);

    @Delete("DELETE FROM api_key_call WHERE created_at < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);
}
