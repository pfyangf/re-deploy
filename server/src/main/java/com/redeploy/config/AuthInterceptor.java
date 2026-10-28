package com.redeploy.config;

import com.redeploy.auth.ApiKeyAccess;
import com.redeploy.auth.AuthPrincipal;
import com.redeploy.auth.MenuAccess;
import com.redeploy.model.ApiKey;
import com.redeploy.service.ApiKeyCallService;
import com.redeploy.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = "authPrincipal";

    private final AuthService authService;
    private final ApiKeyCallService apiKeyCallService;

    public AuthInterceptor(AuthService authService, ApiKeyCallService apiKeyCallService) {
        this.authService = authService;
        this.apiKeyCallService = apiKeyCallService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(response, 401, "未登录");
        }
        String token = authHeader.substring(7).trim();
        AuthPrincipal principal = authService.resolve(token);
        if (principal == null) {
            recordRejectedKey(token, request, 401);
            return reject(response, 401, "登录已失效，请重新登录");
        }
        if ("api-key".equals(principal.getLoginType())) {
            if (!ApiKeyAccess.allows(request.getMethod(), request.getRequestURI())) {
                apiKeyCallService.record(principal.getApiKeyId(), principal.getDisplayName(),
                        request.getMethod(), request.getRequestURI(), 403);
                return reject(response, 403, "API 密钥没有访问该接口的权限");
            }
        } else {
            String requirement = MenuAccess.requirement(request.getMethod(), request.getRequestURI());
            if (!principal.allows(requirement)) {
                return reject(response, 403, "没有访问该功能的权限");
            }
        }
        request.setAttribute(ATTR, principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object principal = request.getAttribute(ATTR);
        if (!(principal instanceof AuthPrincipal auth) || !"api-key".equals(auth.getLoginType())) return;
        apiKeyCallService.record(auth.getApiKeyId(), auth.getDisplayName(),
                request.getMethod(), request.getRequestURI(), response.getStatus());
    }

    private void recordRejectedKey(String token, HttpServletRequest request, int status) {
        if (token == null || !token.startsWith("rdk_")) return;
        ApiKey key = authService.findApiKeyRecord(token);
        Long id = key == null ? null : key.getId();
        String name = key == null || key.getName() == null || key.getName().isBlank() ? "未识别密钥" : key.getName();
        apiKeyCallService.record(id, name, request.getMethod(), request.getRequestURI(), status);
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}
