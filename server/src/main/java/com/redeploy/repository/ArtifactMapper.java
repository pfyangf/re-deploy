package com.redeploy.repository;

import com.redeploy.model.Artifact;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ArtifactMapper {

    @Select("SELECT * FROM artifacts ORDER BY uploaded_at DESC")
    List<Artifact> findAll();

    @Select("SELECT * FROM artifacts WHERE id = #{id}")
    Optional<Artifact> findById(Long id);

    @Select("SELECT * FROM artifacts WHERE file_path = #{filePath}")
    Optional<Artifact> findByFilePath(@Param("filePath") String filePath);

    @Insert("INSERT INTO artifacts (filename, file_path, file_size, md5, uploaded_at) " +
            "VALUES (#{filename}, #{filePath}, #{fileSize}, #{md5}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Artifact artifact);

    @Delete("DELETE FROM artifacts WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM artifacts WHERE file_path = #{filePath}")
    int deleteByFilePath(@Param("filePath") String filePath);
}
