package com.redeploy.controller;

import com.redeploy.service.SystemSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统设置 REST API：
 *  - GET /api/settings  读取全部设置
 *  - PUT /api/settings  更新设置（当前仅 concurrentDeployLimit）
 */
@RestController
@RequestMapping("/api/settings")
public class SystemSettingController {

    private final SystemSettingService settingService;

    public SystemSettingController(SystemSettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    public Map<String, Object> getSettings() {
        return Map.of("concurrentDeployLimit", settingService.getConcurrentDeployLimit());
    }

    @PutMapping
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, Object> body) {
        Object raw = body.get("concurrentDeployLimit");
        if (raw == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 concurrentDeployLimit 参数"));
        }
        int limit;
        try {
            limit = Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "concurrentDeployLimit 必须是整数"));
        }
        try {
            settingService.setConcurrentDeployLimit(limit);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("concurrentDeployLimit", limit));
    }
}
