package com.redeploy.repository;

import com.redeploy.model.AuthSession;
import com.redeploy.model.LoginGuard;
import com.redeploy.model.SysMenu;
import com.redeploy.model.SysRole;
import com.redeploy.model.SysUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RbacMapper {

    @Select("SELECT id, username, password_hash, display_name, enabled, created_at, updated_at FROM rbac_user WHERE username = #{username}")
    SysUser findUserByUsername(String username);

    @Select("SELECT id, username, password_hash, display_name, enabled, created_at, updated_at FROM rbac_user WHERE id = #{id}")
    SysUser findUserById(Long id);

    @Select("SELECT id, username, display_name, enabled, created_at, updated_at FROM rbac_user ORDER BY id")
    List<SysUser> findUsers();

    @Insert("INSERT INTO rbac_user (username, password_hash, display_name, enabled, created_at, updated_at) " +
            "VALUES (#{username}, #{passwordHash}, #{displayName}, #{enabled}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(SysUser user);

    @Update("UPDATE rbac_user SET display_name=#{displayName}, enabled=#{enabled}, password_hash=#{passwordHash}, updated_at=NOW() WHERE id=#{id}")
    int updateUser(SysUser user);

    @Delete("DELETE FROM rbac_user WHERE id = #{id}")
    int deleteUser(Long id);

    @Select("SELECT r.* FROM rbac_role r JOIN rbac_user_role ur ON ur.role_id = r.id WHERE ur.user_id = #{userId} ORDER BY r.id")
    List<SysRole> findRolesByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM rbac_user_role ur JOIN rbac_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.code = 'admin'")
    int countAdminRole(Long userId);

    @Delete("DELETE FROM rbac_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(Long userId);

    @Insert("INSERT INTO rbac_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("SELECT * FROM rbac_role ORDER BY id")
    List<SysRole> findRoles();

    @Select("SELECT * FROM rbac_role WHERE id = #{id}")
    SysRole findRoleById(Long id);

    @Select("SELECT * FROM rbac_role WHERE code = #{code}")
    SysRole findRoleByCode(String code);

    @Insert("INSERT INTO rbac_role (code, name, description, builtin, created_at, updated_at) " +
            "VALUES (#{code}, #{name}, #{description}, #{builtin}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRole(SysRole role);

    @Update("UPDATE rbac_role SET code=#{code}, name=#{name}, description=#{description}, updated_at=NOW() WHERE id=#{id}")
    int updateRole(SysRole role);

    @Delete("DELETE FROM rbac_role WHERE id = #{id}")
    int deleteRole(Long id);

    @Select("SELECT COUNT(*) FROM rbac_user_role WHERE role_id = #{roleId}")
    int countUsersByRole(Long roleId);

    @Select("SELECT menu_id FROM rbac_role_menu WHERE role_id = #{roleId}")
    List<Long> findMenuIdsByRole(Long roleId);

    @Delete("DELETE FROM rbac_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenus(Long roleId);

    @Insert("INSERT IGNORE INTO rbac_role_menu (role_id, menu_id) VALUES (#{roleId}, #{menuId})")
    int insertRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    @Select("SELECT * FROM rbac_menu ORDER BY sort_order, id")
    List<SysMenu> findMenus();

    @Select("SELECT * FROM rbac_menu WHERE id = #{id}")
    SysMenu findMenuById(Long id);

    @Select("SELECT * FROM rbac_menu WHERE path = #{path}")
    SysMenu findMenuByPath(String path);

    @Select("SELECT COUNT(*) FROM rbac_menu WHERE parent_id = #{parentId}")
    int countChildMenus(Long parentId);

    @Select("SELECT m.* FROM rbac_menu m " +
            "JOIN rbac_role_menu rm ON rm.menu_id = m.id " +
            "JOIN rbac_user_role ur ON ur.role_id = rm.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<SysMenu> findMenusByUserId(Long userId);

    @Insert("INSERT INTO rbac_menu (menu_key, parent_id, menu_type, name, path, icon, sort_order, visible, builtin, created_at, updated_at) " +
            "VALUES (#{menuKey}, #{parentId,jdbcType=BIGINT}, #{menuType}, #{name}, #{path,jdbcType=VARCHAR}, #{icon,jdbcType=VARCHAR}, #{sortOrder}, #{visible}, #{builtin}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMenu(SysMenu menu);

    @Update("UPDATE rbac_menu SET parent_id=#{parentId,jdbcType=BIGINT}, menu_type=#{menuType}, name=#{name}, path=#{path,jdbcType=VARCHAR}, icon=#{icon,jdbcType=VARCHAR}, " +
            "sort_order=#{sortOrder}, visible=#{visible}, updated_at=NOW() WHERE id=#{id}")
    int updateMenu(SysMenu menu);

    @Delete("DELETE FROM rbac_menu WHERE id = #{id}")
    int deleteMenu(Long id);

    @Delete("DELETE FROM rbac_role_menu WHERE menu_id = #{menuId}")
    int deleteRoleMenusByMenu(Long menuId);

    @Insert("INSERT INTO rbac_session (token, user_id, login_type, expires_at, created_at) " +
            "VALUES (#{token}, #{userId,jdbcType=BIGINT}, #{loginType}, DATE_ADD(NOW(), INTERVAL 3 DAY), NOW())")
    int insertSession(@Param("token") String token, @Param("userId") Long userId, @Param("loginType") String loginType);

    @Select("SELECT * FROM rbac_session WHERE token = #{token}")
    AuthSession findSession(String token);

    @Delete("DELETE FROM rbac_session WHERE token = #{token}")
    int deleteSession(String token);

    @Delete("DELETE FROM rbac_session WHERE user_id = #{userId}")
    int deleteSessionsByUser(Long userId);

    @Delete("DELETE FROM rbac_session WHERE expires_at < NOW()")
    int deleteExpiredSessions();

    @Select("SELECT * FROM rbac_login_guard WHERE client_key = #{clientKey}")
    LoginGuard findGuard(String clientKey);

    @Insert("INSERT INTO rbac_login_guard (client_key, captcha_code, captcha_expire_at, trust_until, force_captcha, updated_at) " +
            "VALUES (#{clientKey}, #{captchaCode,jdbcType=VARCHAR}, #{captchaExpireAt,jdbcType=TIMESTAMP}, " +
            "#{trustUntil,jdbcType=TIMESTAMP}, #{forceCaptcha}, NOW())")
    int insertGuard(LoginGuard guard);

    @Update("UPDATE rbac_login_guard SET captcha_code=#{captchaCode,jdbcType=VARCHAR}, " +
            "captcha_expire_at=#{captchaExpireAt,jdbcType=TIMESTAMP}, trust_until=#{trustUntil,jdbcType=TIMESTAMP}, " +
            "force_captcha=#{forceCaptcha}, updated_at=NOW() WHERE client_key=#{clientKey}")
    int updateGuard(LoginGuard guard);
}
