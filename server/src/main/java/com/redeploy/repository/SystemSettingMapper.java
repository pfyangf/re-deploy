package com.redeploy.repository;

import com.redeploy.model.SystemSetting;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SystemSettingMapper {

    @Select("SELECT * FROM system_settings ORDER BY id")
    List<SystemSetting> findAll();

    @Select("SELECT * FROM system_settings WHERE setting_key = #{key}")
    SystemSetting findByKey(@Param("key") String key);

    @Insert("INSERT INTO system_settings (setting_key, setting_value, created_at, updated_at) " +
            "VALUES (#{key}, #{value}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE setting_value = #{value}, updated_at = NOW()")
    int upsert(@Param("key") String key, @Param("value") String value);
}
