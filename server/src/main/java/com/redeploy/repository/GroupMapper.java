package com.redeploy.repository;

import com.redeploy.model.Group;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Optional;

@Mapper
public interface GroupMapper {

    @Select("SELECT * FROM `groups` ORDER BY name")
    List<Group> findAll();

    @Select("SELECT * FROM `groups` WHERE id = #{id}")
    Optional<Group> findById(Long id);

    @Select("SELECT * FROM `groups` WHERE name = #{name}")
    Optional<Group> findByName(String name);

    @Insert("INSERT INTO `groups` (name, description, created_at, updated_at) " +
            "VALUES (#{name}, #{description}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Group group);

    @Update("UPDATE `groups` SET name=#{name}, description=#{description}, updated_at=NOW() WHERE id=#{id}")
    int update(Group group);

    @Delete("DELETE FROM `groups` WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM `groups` WHERE id = #{id}")
    boolean existsById(Long id);

    @Select("SELECT COUNT(*) FROM `groups`")
    long count();
}
