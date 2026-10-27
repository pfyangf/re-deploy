package com.redeploy.service;

import com.redeploy.auth.AuthException;
import com.redeploy.auth.AuthPrincipal;
import com.redeploy.auth.CaptchaImage;
import com.redeploy.model.AuthSession;
import com.redeploy.model.LoginGuard;
import com.redeploy.model.SysMenu;
import com.redeploy.model.SysUser;
import com.redeploy.repository.RbacMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuthService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RbacMapper rbacMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String adminToken;

    public AuthService(RbacMapper rbacMapper, @Value("${redeploy.admin-token}") String adminToken) {
        this.rbacMapper = rbacMapper;
        this.adminToken = adminToken;
    }

    public Map<String, Object> login(Map<String, Object> body) {
        String type = text(body.get("type"));
        if ("token".equals(type)) {
            if (!constantEquals(text(body.get("token")), adminToken)) {
                throw new AuthException(401, "密钥不正确");
            }
            AuthPrincipal principal = new AuthPrincipal(true, null, "admin", "管理员", "token", Set.of());
            return issue(null, "token", principal);
        }
        if (!"password".equals(type)) {
            throw new AuthException(400, "不支持的登录方式");
        }
        String username = text(body.get("username")).trim();
        String password = text(body.get("password"));
        String clientKey = normalizeClient(text(body.get("clientKey")));
        if (username.isEmpty() || password.isEmpty()) {
            throw new AuthException(400, "请输入用户名和密码", captchaRequired(clientKey));
        }
        boolean needCaptcha = captchaRequired(clientKey);
        if (needCaptcha) {
            String captcha = text(body.get("captcha")).trim();
            if (captcha.isEmpty()) {
                throw new AuthException(400, "请输入验证码", true);
            }
            if (!consumeCaptcha(clientKey, captcha)) {
                throw new AuthException(400, "验证码错误", true);
            }
        }
        SysUser user = rbacMapper.findUserByUsername(username);
        boolean passwordOk = false;
        if (user != null && user.getPasswordHash() != null) {
            try {
                passwordOk = passwordEncoder.matches(password, user.getPasswordHash());
            } catch (Exception ignored) {
                passwordOk = false;
            }
        }
        if (!passwordOk) {
            forceCaptcha(clientKey);
            throw new AuthException(401, "用户名或密码错误", true);
        }
        if (user.getEnabled() == null || user.getEnabled() == 0) {
            throw new AuthException(403, "账号已停用");
        }
        if (needCaptcha) {
            grantTrust(clientKey);
        }
        return issue(user.getId(), "password", principalOf(user, "password"));
    }

    public boolean captchaRequired(String clientKey) {
        LoginGuard guard = rbacMapper.findGuard(clientKey);
        if (guard == null) return true;
        if (guard.getForceCaptcha() != null && guard.getForceCaptcha() == 1) return true;
        return guard.getTrustUntil() == null || !guard.getTrustUntil().isAfter(LocalDateTime.now());
    }

    public byte[] issueCaptcha(String clientKey) {
        LoginGuard guard = loadGuard(clientKey);
        String code = CaptchaImage.randomCode();
        guard.setCaptchaCode(code);
        guard.setCaptchaExpireAt(LocalDateTime.now().plusMinutes(5));
        saveGuard(guard);
        try {
            return CaptchaImage.png(code);
        } catch (Exception e) {
            throw new AuthException(500, "验证码生成失败");
        }
    }

    public AuthPrincipal resolve(String token) {
        if (token == null || token.isBlank()) return null;
        if (constantEquals(token, adminToken)) {
            return new AuthPrincipal(true, null, "admin", "管理员", "admin-token", Set.of());
        }
        AuthSession session = rbacMapper.findSession(token);
        if (session == null) return null;
        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            rbacMapper.deleteSession(token);
            return null;
        }
        if (session.getUserId() == null) {
            return new AuthPrincipal(true, null, "admin", "管理员", session.getLoginType(), Set.of());
        }
        SysUser user = rbacMapper.findUserById(session.getUserId());
        if (user == null || user.getEnabled() == null || user.getEnabled() == 0) {
            rbacMapper.deleteSessionsByUser(session.getUserId());
            return null;
        }
        return principalOf(user, session.getLoginType());
    }

    public Map<String, Object> me(String bearerToken, AuthPrincipal principal) {
        Long expiresAt = null;
        if (!constantEquals(bearerToken, adminToken)) {
            AuthSession session = rbacMapper.findSession(bearerToken);
            if (session != null && session.getExpiresAt() != null) {
                expiresAt = epoch(session.getExpiresAt());
            }
        }
        return profile(principal, expiresAt);
    }

    public void logout(String bearerToken) {
        if (bearerToken != null && !bearerToken.isBlank() && !constantEquals(bearerToken, adminToken)) {
            rbacMapper.deleteSession(bearerToken);
        }
    }

    public int purgeExpired() {
        return rbacMapper.deleteExpiredSessions();
    }

    public String normalizeClient(String clientKey) {
        String key = clientKey == null ? "" : clientKey.trim();
        if (!key.matches("[a-zA-Z0-9]{16,64}")) {
            throw new AuthException(400, "客户端标识无效");
        }
        return key;
    }

    private Map<String, Object> issue(Long userId, String loginType, AuthPrincipal principal) {
        String token = newToken();
        rbacMapper.insertSession(token, userId, loginType);
        AuthSession session = rbacMapper.findSession(token);
        Map<String, Object> body = profile(principal, session == null ? null : epoch(session.getExpiresAt()));
        body.put("token", token);
        return body;
    }

    private Map<String, Object> profile(AuthPrincipal principal, Long expiresAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", principal.getUsername());
        body.put("displayName", principal.getDisplayName());
        body.put("admin", principal.isAdmin());
        body.put("loginType", principal.getLoginType());
        body.put("expiresAt", expiresAt);
        body.put("menus", visibleMenus(principal));
        return body;
    }

    private AuthPrincipal principalOf(SysUser user, String loginType) {
        boolean admin = rbacMapper.countAdminRole(user.getId()) > 0;
        Set<String> paths = new HashSet<>();
        if (!admin) {
            for (SysMenu menu : rbacMapper.findMenusByUserId(user.getId())) {
                if (menu.getPath() != null && !menu.getPath().isBlank()
                        && (menu.getVisible() == null || menu.getVisible() == 1)) {
                    paths.add(menu.getPath());
                }
            }
        }
        String display = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
        return new AuthPrincipal(admin, user.getId(), user.getUsername(), display, loginType, paths);
    }

    private List<Map<String, Object>> visibleMenus(AuthPrincipal principal) {
        List<SysMenu> all = rbacMapper.findMenus();
        Map<Long, SysMenu> byId = new HashMap<>();
        for (SysMenu menu : all) byId.put(menu.getId(), menu);

        List<SysMenu> granted = new ArrayList<>();
        if (principal.isAdmin()) {
            granted.addAll(all);
        } else if (principal.getUserId() != null) {
            Set<Long> seen = new HashSet<>();
            for (SysMenu menu : rbacMapper.findMenusByUserId(principal.getUserId())) {
                if (seen.add(menu.getId())) granted.add(menu);
                Long parentId = menu.getParentId();
                while (parentId != null && seen.add(parentId)) {
                    SysMenu parent = byId.get(parentId);
                    if (parent == null) break;
                    granted.add(parent);
                    parentId = parent.getParentId();
                }
            }
        }
        List<Map<String, Object>> views = new ArrayList<>();
        for (SysMenu menu : granted) {
            if (menu.getVisible() != null && menu.getVisible() == 0) continue;
            views.add(menuView(menu));
        }
        return views;
    }

    static Map<String, Object> menuView(SysMenu menu) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", menu.getId());
        view.put("parentId", menu.getParentId());
        view.put("menuType", menu.getMenuType());
        view.put("name", menu.getName());
        view.put("path", menu.getPath());
        view.put("icon", menu.getIcon());
        view.put("sortOrder", menu.getSortOrder());
        view.put("visible", menu.getVisible());
        view.put("builtin", menu.getBuiltin());
        return view;
    }

    private void forceCaptcha(String clientKey) {
        LoginGuard guard = loadGuard(clientKey);
        guard.setForceCaptcha(1);
        guard.setCaptchaCode(null);
        guard.setCaptchaExpireAt(null);
        saveGuard(guard);
    }

    private void grantTrust(String clientKey) {
        LoginGuard guard = loadGuard(clientKey);
        guard.setForceCaptcha(0);
        guard.setTrustUntil(LocalDateTime.now().plusDays(7));
        guard.setCaptchaCode(null);
        guard.setCaptchaExpireAt(null);
        saveGuard(guard);
    }

    private boolean consumeCaptcha(String clientKey, String input) {
        LoginGuard guard = rbacMapper.findGuard(clientKey);
        if (guard == null || guard.getCaptchaCode() == null || guard.getCaptchaCode().isBlank()) return false;
        boolean fresh = guard.getCaptchaExpireAt() != null && guard.getCaptchaExpireAt().isAfter(LocalDateTime.now());
        boolean match = fresh && guard.getCaptchaCode().equalsIgnoreCase(input.trim());
        guard.setCaptchaCode(null);
        guard.setCaptchaExpireAt(null);
        saveGuard(guard);
        return match;
    }

    private LoginGuard loadGuard(String clientKey) {
        LoginGuard guard = rbacMapper.findGuard(clientKey);
        if (guard != null) return guard;
        guard = new LoginGuard();
        guard.setClientKey(clientKey);
        guard.setForceCaptcha(0);
        return guard;
    }

    private void saveGuard(LoginGuard guard) {
        if (rbacMapper.findGuard(guard.getClientKey()) == null) {
            if (guard.getForceCaptcha() == null) guard.setForceCaptcha(0);
            rbacMapper.insertGuard(guard);
        } else {
            rbacMapper.updateGuard(guard);
        }
    }

    private String newToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return java.util.HexFormat.of().formatHex(buf);
    }

    private long epoch(LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }

    private boolean constantEquals(String raw, String expected) {
        if (raw == null || expected == null) return false;
        return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
