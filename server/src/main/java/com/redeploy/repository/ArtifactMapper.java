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

    @Insert("INSERT INTO artifacts (filename, file_path, file_size, md5, uploaded_at) " +
            "VALUES (#{filename}, #{filePath}, #{fileSize}, #{md5}, datetime('now'))")
    @SelectKey(statement = "SELECT last_insert_rowid()", keyProperty = "id", before = false, resultType = Long.class)
    int insert(Artifact artifact);

    @Delete("DELETE FROM artifacts WHERE id = #{id}")
    int deleteById(Long id);
}
