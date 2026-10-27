package com.redeploy.controller;

import com.redeploy.auth.AuthException;
import com.redeploy.auth.AuthPrincipal;
import com.redeploy.config.AuthInterceptor;
import com.redeploy.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(authService.login(body == null ? Map.of() : body));
        } catch (AuthException e) {
            return error(e);
        }
    }

    @GetMapping("/captcha/status")
    public ResponseEntity<?> captchaStatus(@RequestParam String clientKey) {
        try {
            String key = authService.normalizeClient(clientKey);
            Map<String, Object> body = new HashMap<>();
            body.put("required", authService.captchaRequired(key));
            return ResponseEntity.ok(body);
        } catch (AuthException e) {
            return error(e);
        }
    }

    @GetMapping("/captcha")
    public ResponseEntity<?> captcha(@RequestParam String clientKey) {
        try {
            String key = authService.normalizeClient(clientKey);
            byte[] png = authService.issueCaptcha(key);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .body(png);
        } catch (AuthException e) {
            return error(e);
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute(AuthInterceptor.ATTR);
        return authService.me(bearer(request), principal);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        authService.logout(bearer(request));
        return Map.of("ok", true);
    }

    private String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7).trim();
        return "";
    }

    private ResponseEntity<Map<String, Object>> error(AuthException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", e.getMessage());
        body.put("captchaRequired", e.isCaptchaRequired());
        return ResponseEntity.status(e.getStatus()).body(body);
    }
}
