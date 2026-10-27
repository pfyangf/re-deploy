package com.redeploy.auth;

import java.util.Collections;
import java.util.Set;

/** 当前请求的登录身份。密钥登录和管理员角色拥有全部菜单。 */
public class AuthPrincipal {
    private final boolean admin;
    private final Long userId;
    private final String username;
    private final String displayName;
    private final String loginType;
    private final Set<String> menuPaths;

    public AuthPrincipal(boolean admin, Long userId, String username, String displayName,
                         String loginType, Set<String> menuPaths) {
        this.admin = admin;
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.loginType = loginType;
        this.menuPaths = menuPaths == null ? Set.of() : Set.copyOf(menuPaths);
    }

    public boolean isAdmin() { return admin; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getLoginType() { return loginType; }
    public Set<String> getMenuPaths() { return Collections.unmodifiableSet(menuPaths); }

    public boolean allows(String requirement) {
        if (requirement == null) return true;
        if (admin) return true;
        if ("ADMIN".equals(requirement)) return false;
        if (requirement.startsWith("ANY:")) {
            for (String path : requirement.substring(4).split(",")) {
                if (menuPaths.contains(path)) return true;
            }
            return false;
        }
        return menuPaths.contains(requirement);
    }
}
