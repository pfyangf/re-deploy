package com.redeploy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${redeploy.admin-token}")
    private String adminToken;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(adminToken))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/agents/register",
                    "/api/agents/heartbeat",
                    "/api/health",
                    "/api/agent/download/**",
                    "/api/agent/install.sh"
                );
    }

    public static class AuthInterceptor implements HandlerInterceptor {
        
        private final String adminToken;

        public AuthInterceptor(String adminToken) {
            this.adminToken = adminToken;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                                 Object handler) throws Exception {
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
                return false;
            }

            String token = authHeader.substring(7);
            
            if (!adminToken.equals(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid token\"}");
                return false;
            }

            return true;
        }
    }
}
