package com.redeploy.config;

import com.redeploy.auth.AuthPrincipal;
import com.redeploy.auth.MenuAccess;
import com.redeploy.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR = "authPrincipal";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(response, 401, "未登录");
        }
        AuthPrincipal principal = authService.resolve(authHeader.substring(7).trim());
        if (principal == null) {
            return reject(response, 401, "登录已失效，请重新登录");
        }
        String requirement = MenuAccess.requirement(request.getMethod(), request.getRequestURI());
        if (!principal.allows(requirement)) {
            return reject(response, 403, "没有访问该功能的权限");
        }
        request.setAttribute(ATTR, principal);
        return true;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}
