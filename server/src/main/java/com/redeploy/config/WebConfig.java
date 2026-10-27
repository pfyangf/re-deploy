package com.redeploy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/agents/register",
                        "/api/agents/heartbeat",
                        "/api/health",
                        "/api/agent/download/**",
                        "/api/agent/install.sh",
                        "/api/auth/login",
                        "/api/auth/captcha",
                        "/api/auth/captcha/status"
                );
    }
}
