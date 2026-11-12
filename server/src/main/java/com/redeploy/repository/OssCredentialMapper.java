package com.redeploy.repository;

import com.redeploy.model.OssCredential;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OssCredentialMapper {

    @Select("SELECT * FROM oss_credentials ORDER BY id")
    List<OssCredential> findAll();

    @Select("SELECT * FROM oss_credentials WHERE id = #{id}")
    Optional<OssCredential> findById(Long id);

    @Select("SELECT * FROM oss_credentials WHERE name = #{name}")
    Optional<OssCredential> findByName(String name);

    @Select("SELECT COUNT(*) FROM oss_credentials WHERE id = #{id}")
    int countById(Long id);

    @Insert("INSERT INTO oss_credentials (name, endpoint, access_key_id, access_key_secret, created_at, updated_at) " +
            "VALUES (#{name}, #{endpoint}, #{accessKeyId}, #{accessKeySecret}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OssCredential credential);

    @Update("UPDATE oss_credentials SET name=#{name}, endpoint=#{endpoint}, access_key_id=#{accessKeyId}, " +
            "access_key_secret=#{accessKeySecret}, updated_at=NOW() WHERE id=#{id}")
    int update(OssCredential credential);

    @Delete("DELETE FROM oss_credentials WHERE id = #{id}")
    int deleteById(Long id);
}
