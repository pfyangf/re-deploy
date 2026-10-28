package com.redeploy.service;

import com.redeploy.model.SysMenu;
import com.redeploy.model.SysRole;
import com.redeploy.model.SysUser;
import com.redeploy.repository.RbacMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RbacService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final RbacMapper rbacMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public RbacService(RbacMapper rbacMapper) {
        this.rbacMapper = rbacMapper;
    }

    public List<Map<String, Object>> listUsers() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUser user : rbacMapper.findUsers()) {
            rows.add(userView(user, rbacMapper.findRolesByUserId(user.getId())));
        }
        return rows;
    }

    public Map<String, Object> createUser(Map<String, Object> body) {
        String username = required(body.get("username"), "用户名不能为空").trim();
        if (!username.matches("[a-zA-Z][a-zA-Z0-9_]{2,31}")) {
            throw new IllegalArgumentException("用户名需以字母开头，3–32 位字母、数字或下划线");
        }
        if (rbacMapper.findUserByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        String password = required(body.get("password"), "密码不能为空");
        checkPassword(password);
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName(body.get("displayName"), username));
        user.setEnabled(enabledOf(body.get("enabled"), 1));
        rbacMapper.insertUser(user);
        replaceUserRoles(user.getId(), ids(body.get("roleIds")));
        return userView(rbacMapper.findUserById(user.getId()), rbacMapper.findRolesByUserId(user.getId()));
    }

    public Map<String, Object> updateUser(Long id, Map<String, Object> body, Long currentUserId) {
        SysUser user = rbacMapper.findUserById(id);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        int enabled = enabledOf(body.get("enabled"), user.getEnabled() == null ? 1 : user.getEnabled());
        if (currentUserId != null && currentUserId.equals(id) && enabled == 0) {
            throw new IllegalArgumentException("不能停用当前登录用户");
        }
        user.setDisplayName(displayName(body.get("displayName"), user.getUsername()));
        user.setEnabled(enabled);
        String password = text(body.get("password"));
        if (!password.isBlank()) {
            checkPassword(password);
            user.setPasswordHash(passwordEncoder.encode(password));
            rbacMapper.deleteSessionsByUser(id);
        }
        rbacMapper.updateUser(user);
        if (enabled == 0) rbacMapper.deleteSessionsByUser(id);
        if (body.containsKey("roleIds")) replaceUserRoles(id, ids(body.get("roleIds")));
        return userView(rbacMapper.findUserById(id), rbacMapper.findRolesByUserId(id));
    }

    public void deleteUser(Long id, Long currentUserId) {
        if (rbacMapper.findUserById(id) == null) throw new IllegalArgumentException("用户不存在");
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new IllegalArgumentException("不能删除当前登录用户");
        }
        rbacMapper.deleteUserRoles(id);
        rbacMapper.deleteSessionsByUser(id);
        rbacMapper.deleteUser(id);
    }

    public List<Map<String, Object>> listRoles() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysRole role : rbacMapper.findRoles()) {
            rows.add(roleView(role));
        }
        return rows;
    }

    public Map<String, Object> createRole(Map<String, Object> body) {
        SysRole role = new SysRole();
        role.setCode(roleCode(required(body.get("code"), "角色编码不能为空"), null));
        role.setName(roleName(body.get("name")));
        role.setDescription(limit(text(body.get("description")), 200));
        role.setBuiltin(0);
        if (rbacMapper.findRoleByCode(role.getCode()) != null) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        rbacMapper.insertRole(role);
        if (body.containsKey("menuIds")) replaceRoleMenus(role, ids(body.get("menuIds")));
        return roleView(rbacMapper.findRoleById(role.getId()));
    }

    public Map<String, Object> updateRole(Long id, Map<String, Object> body) {
        SysRole role = mustRole(id);
        if (isAdminRole(role)) {
            role.setName(roleName(body.get("name")));
            role.setDescription(limit(text(body.get("description")), 200));
            rbacMapper.updateRole(role);
            return roleView(rbacMapper.findRoleById(id));
        }
        String code = roleCode(required(body.get("code"), "角色编码不能为空"), role.getCode());
        if (rbacMapper.findRoleByCode(code) != null && !code.equals(role.getCode())) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        role.setCode(code);
        role.setName(roleName(body.get("name")));
        role.setDescription(limit(text(body.get("description")), 200));
        rbacMapper.updateRole(role);
        return roleView(rbacMapper.findRoleById(id));
    }

    public void deleteRole(Long id) {
        SysRole role = mustRole(id);
        if (isAdminRole(role) || (role.getBuiltin() != null && role.getBuiltin() == 1)) {
            throw new IllegalArgumentException("内置角色不可删除");
        }
        if (rbacMapper.countUsersByRole(id) > 0) {
            throw new IllegalArgumentException("仍有用户使用该角色");
        }
        rbacMapper.deleteRoleMenus(id);
        rbacMapper.deleteRole(id);
    }

    public Map<String, Object> assignRoleMenus(Long id, List<Long> menuIds) {
        SysRole role = mustRole(id);
        replaceRoleMenus(role, menuIds == null ? List.of() : menuIds);
        return roleView(rbacMapper.findRoleById(id));
    }

    public List<Map<String, Object>> listMenus() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysMenu menu : rbacMapper.findMenus()) {
            rows.add(AuthService.menuView(menu));
        }
        return rows;
    }

    public Map<String, Object> createMenu(Map<String, Object> body) {
        SysMenu menu = new SysMenu();
        menu.setMenuKey("custom-" + java.util.HexFormat.of().formatHex(randomBytes(4)));
        menu.setBuiltin(0);
        applyMenu(menu, body, false);
        rbacMapper.insertMenu(menu);
        attachAdmin(menu.getId());
        return AuthService.menuView(rbacMapper.findMenuById(menu.getId()));
    }

    public Map<String, Object> updateMenu(Long id, Map<String, Object> body) {
        SysMenu menu = rbacMapper.findMenuById(id);
        if (menu == null) throw new IllegalArgumentException("菜单不存在");
        boolean builtin = menu.getBuiltin() != null && menu.getBuiltin() == 1;
        applyMenu(menu, body, builtin);
        rbacMapper.updateMenu(menu);
        return AuthService.menuView(rbacMapper.findMenuById(id));
    }

    public void deleteMenu(Long id) {
        SysMenu menu = rbacMapper.findMenuById(id);
        if (menu == null) throw new IllegalArgumentException("菜单不存在");
        if (menu.getBuiltin() != null && menu.getBuiltin() == 1) {
            throw new IllegalArgumentException("内置菜单不可删除");
        }
        if (rbacMapper.countChildMenus(id) > 0) {
            throw new IllegalArgumentException("请先删除下级菜单");
        }
        rbacMapper.deleteRoleMenusByMenu(id);
        rbacMapper.deleteMenu(id);
    }

    /** 启动时补齐内置菜单和管理员角色，并把全部菜单赋给管理员。 */
    public void seed() {
        record Seed(String key, String parent, String type, String name, String path, String icon, int sort) {}
        Seed[] seeds = {
                new Seed("nav.overview", null, "group", "总览", null, null, 10),
                new Seed("nav.resource", null, "group", "资源", null, null, 20),
                new Seed("nav.deploy", null, "group", "部署", null, null, 30),
                new Seed("nav.ops", null, "group", "运维", null, null, 40),
                new Seed("nav.system", null, "group", "系统", null, null, 50),
                new Seed("nav.access", null, "group", "权限", null, null, 60),
                new Seed("dashboard", "nav.overview", "menu", "仪表盘", "/dashboard", "Odometer", 11),
                new Seed("groups", "nav.resource", "menu", "分组管理", "/groups", "Folder", 21),
                new Seed("servers", "nav.resource", "menu", "服务器管理", "/servers", "Monitor", 22),
                new Seed("tasks", "nav.resource", "menu", "任务管理", "/tasks", "List", 23),
                new Seed("deploy", "nav.deploy", "menu", "部署操作", "/deploy", "Promotion", 31),
                new Seed("history", "nav.deploy", "menu", "部署历史", "/history", "Clock", 32),
                new Seed("artifacts", "nav.deploy", "menu", "构建产物", "/artifacts", "Files", 33),
                new Seed("notifications", "nav.ops", "menu", "通知管理", "/notifications", "Bell", 41),
                new Seed("downloads", "nav.ops", "menu", "远端下载", "/downloads", "Download", 42),
                new Seed("settings", "nav.system", "menu", "系统设置", "/settings", "Setting", 51),
                new Seed("access.users", "nav.access", "menu", "用户管理", "/access/users", "User", 61),
                new Seed("access.roles", "nav.access", "menu", "角色管理", "/access/roles", "UserFilled", 62),
                new Seed("access.menus", "nav.access", "menu", "菜单管理", "/access/menus", "Menu", 63),
                new Seed("access.api-keys", "nav.access", "menu", "API 密钥", "/access/api-keys", "Key", 64)
        };
        Map<String, Long> ids = new LinkedHashMap<>();
        for (SysMenu existing : rbacMapper.findMenus()) {
            ids.put(existing.getMenuKey(), existing.getId());
        }
        for (Seed seed : seeds) {
            if (ids.containsKey(seed.key)) continue;
            SysMenu menu = new SysMenu();
            menu.setMenuKey(seed.key);
            menu.setParentId(seed.parent == null ? null : ids.get(seed.parent));
            menu.setMenuType(seed.type);
            menu.setName(seed.name);
            menu.setPath(seed.path);
            menu.setIcon(seed.icon);
            menu.setSortOrder(seed.sort);
            menu.setVisible(1);
            menu.setBuiltin(1);
            rbacMapper.insertMenu(menu);
            ids.put(seed.key, menu.getId());
        }
        SysRole admin = rbacMapper.findRoleByCode("admin");
        if (admin == null) {
            admin = new SysRole();
            admin.setCode("admin");
            admin.setName("管理员");
            admin.setDescription("拥有全部菜单");
            admin.setBuiltin(1);
            rbacMapper.insertRole(admin);
        }
        for (SysMenu menu : rbacMapper.findMenus()) {
            rbacMapper.insertRoleMenu(admin.getId(), menu.getId());
        }
    }

    private void applyMenu(SysMenu menu, Map<String, Object> body, boolean builtin) {
        menu.setName(limit(required(body.get("name"), "菜单名称不能为空").trim(), 32));
        menu.setIcon(blankToNull(limit(text(body.get("icon")).trim(), 64)));
        menu.setSortOrder(intOf(body.get("sortOrder"), menu.getSortOrder() == null ? 0 : menu.getSortOrder()));
        menu.setVisible(enabledOf(body.get("visible"), 1));
        if (builtin) return;
        String type = text(body.get("menuType")).trim();
        if (!"group".equals(type) && !"menu".equals(type)) {
            throw new IllegalArgumentException("菜单类型只能是分组或菜单");
        }
        menu.setMenuType(type);
        Long parentId = longOrNull(body.get("parentId"));
        if ("group".equals(type)) {
            menu.setParentId(null);
            menu.setPath(null);
            return;
        }
        if (parentId == null) throw new IllegalArgumentException("请选择上级分组");
        SysMenu parent = rbacMapper.findMenuById(parentId);
        if (parent == null || !"group".equals(parent.getMenuType())) {
            throw new IllegalArgumentException("上级必须是分组");
        }
        String path = required(body.get("path"), "菜单路径不能为空").trim();
        if (!path.startsWith("/") || path.length() > 128) {
            throw new IllegalArgumentException("路径需以 / 开头");
        }
        SysMenu same = rbacMapper.findMenuByPath(path);
        if (same != null && (menu.getId() == null || !same.getId().equals(menu.getId()))) {
            throw new IllegalArgumentException("菜单路径已存在");
        }
        menu.setParentId(parentId);
        menu.setPath(path);
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        rbacMapper.deleteUserRoles(userId);
        for (Long roleId : roleIds) {
            if (rbacMapper.findRoleById(roleId) == null) {
                throw new IllegalArgumentException("角色不存在");
            }
            rbacMapper.insertUserRole(userId, roleId);
        }
    }

    private void replaceRoleMenus(SysRole role, List<Long> menuIds) {
        if (isAdminRole(role)) {
            throw new IllegalArgumentException("管理员角色固定拥有全部菜单");
        }
        rbacMapper.deleteRoleMenus(role.getId());
        for (Long menuId : menuIds) {
            if (rbacMapper.findMenuById(menuId) == null) {
                throw new IllegalArgumentException("菜单不存在");
            }
            rbacMapper.insertRoleMenu(role.getId(), menuId);
        }
    }

    private void attachAdmin(Long menuId) {
        SysRole admin = rbacMapper.findRoleByCode("admin");
        if (admin != null) rbacMapper.insertRoleMenu(admin.getId(), menuId);
    }

    private Map<String, Object> userView(SysUser user, List<SysRole> roles) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("username", user.getUsername());
        view.put("displayName", user.getDisplayName());
        view.put("enabled", user.getEnabled());
        view.put("createdAt", user.getCreatedAt());
        List<Map<String, Object>> roleViews = new ArrayList<>();
        for (SysRole role : roles) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", role.getId());
            item.put("code", role.getCode());
            item.put("name", role.getName());
            roleViews.add(item);
        }
        view.put("roles", roleViews);
        return view;
    }

    private Map<String, Object> roleView(SysRole role) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", role.getId());
        view.put("code", role.getCode());
        view.put("name", role.getName());
        view.put("description", role.getDescription());
        view.put("builtin", role.getBuiltin());
        view.put("menuIds", rbacMapper.findMenuIdsByRole(role.getId()));
        view.put("userCount", rbacMapper.countUsersByRole(role.getId()));
        return view;
    }

    private SysRole mustRole(Long id) {
        SysRole role = rbacMapper.findRoleById(id);
        if (role == null) throw new IllegalArgumentException("角色不存在");
        return role;
    }

    private boolean isAdminRole(SysRole role) {
        return role != null && "admin".equals(role.getCode());
    }

    private String roleCode(String raw, String current) {
        String code = raw.trim();
        if ("admin".equals(code) && !"admin".equals(current)) {
            throw new IllegalArgumentException("admin 是内置角色编码");
        }
        if (!code.matches("[a-z][a-z0-9_]{1,31}")) {
            throw new IllegalArgumentException("编码需以小写字母开头，2–32 位小写字母、数字或下划线");
        }
        return code;
    }

    private String roleName(Object raw) {
        String name = required(raw, "角色名称不能为空").trim();
        if (name.length() > 32) throw new IllegalArgumentException("角色名称最多 32 字");
        return name;
    }

    private void checkPassword(String password) {
        if (password.length() < 6 || password.length() > 64) {
            throw new IllegalArgumentException("密码长度需在 6–64 之间");
        }
    }

    private String displayName(Object raw, String fallback) {
        String name = text(raw).trim();
        if (name.isEmpty()) name = fallback;
        if (name.length() > 32) throw new IllegalArgumentException("显示名最多 32 字");
        return name;
    }

    private int enabledOf(Object raw, int fallback) {
        if (raw == null || String.valueOf(raw).isBlank()) return fallback;
        String value = String.valueOf(raw);
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) return 1;
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) return 0;
        throw new IllegalArgumentException("启用状态无效");
    }

    private int intOf(Object raw, int fallback) {
        if (raw == null || String.valueOf(raw).isBlank()) return fallback;
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("排序必须是数字");
        }
    }

    private Long longOrNull(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("上级菜单无效");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> ids(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("关联数据格式不正确");
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            if (item == null || String.valueOf(item).isBlank()) continue;
            try {
                ids.add(Long.parseLong(String.valueOf(item)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("关联数据无效");
            }
        }
        return ids;
    }

    private String required(Object raw, String message) {
        String value = text(raw).trim();
        if (value.isEmpty()) throw new IllegalArgumentException(message);
        return value;
    }

    private String limit(String value, int max) {
        if (value.length() > max) throw new IllegalArgumentException("内容超出长度限制");
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private byte[] randomBytes(int n) {
        byte[] buf = new byte[n];
        RANDOM.nextBytes(buf);
        return buf;
    }
}
