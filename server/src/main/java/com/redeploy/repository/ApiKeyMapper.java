package com.redeploy.repository;

import com.redeploy.model.ApiKey;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApiKeyMapper {

    @Select("SELECT k.id, k.name, k.key_prefix, k.token_hash, k.enabled, k.user_id, k.expires_at, k.last_used_at, k.created_at, " +
            "u.username AS owner_username, u.display_name AS owner_display_name " +
            "FROM api_key k LEFT JOIN rbac_user u ON u.id = k.user_id ORDER BY k.id DESC")
    List<ApiKey> findAll();

    @Select("SELECT k.id, k.name, k.key_prefix, k.token_hash, k.enabled, k.user_id, k.expires_at, k.last_used_at, k.created_at, " +
            "u.username AS owner_username, u.display_name AS owner_display_name " +
            "FROM api_key k LEFT JOIN rbac_user u ON u.id = k.user_id WHERE k.id = #{id}")
    ApiKey findById(Long id);

    @Select("SELECT k.id, k.name, k.key_prefix, k.token_hash, k.enabled, k.user_id, k.expires_at, k.last_used_at, k.created_at, " +
            "u.username AS owner_username, u.display_name AS owner_display_name " +
            "FROM api_key k LEFT JOIN rbac_user u ON u.id = k.user_id WHERE k.token_hash = #{tokenHash}")
    ApiKey findByHash(String tokenHash);

    @Select("SELECT COUNT(*) FROM api_key WHERE user_id = #{userId} AND name = #{name}")
    int countByUserAndName(@Param("userId") Long userId, @Param("name") String name);

    @Insert("INSERT INTO api_key (name, key_prefix, token_hash, enabled, user_id, expires_at, created_at) " +
            "VALUES (#{name}, #{keyPrefix}, #{tokenHash}, 1, #{userId}, #{expiresAt}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApiKey key);

    @Update("UPDATE api_key SET enabled = #{enabled} WHERE id = #{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    @Update("UPDATE api_key SET last_used_at = NOW() WHERE id = #{id} " +
            "AND (last_used_at IS NULL OR last_used_at < DATE_SUB(NOW(), INTERVAL 1 MINUTE))")
    int touchLastUsed(Long id);

    @Delete("DELETE FROM api_key WHERE id = #{id}")
    int delete(Long id);
}
