package com.redeploy.controller;

import com.redeploy.service.ApiKeyCallService;
import com.redeploy.service.ApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ApiKeyCallService apiKeyCallService;

    public ApiKeyController(ApiKeyService apiKeyService, ApiKeyCallService apiKeyCallService) {
        this.apiKeyService = apiKeyService;
        this.apiKeyCallService = apiKeyCallService;
    }

    @GetMapping
    public Object list() {
        return apiKeyService.list();
    }

    @GetMapping("/owners")
    public Object owners() {
        return apiKeyService.owners();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(defaultValue = "day") String unit,
                                     @RequestParam(defaultValue = "7") int span,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return apiKeyCallService.stats(unit, span, page, size);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        return ok(() -> apiKeyService.create(body == null ? Map.of() : body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ok(() -> apiKeyService.updateEnabled(id, body == null ? Map.of() : body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return ok(() -> {
            apiKeyService.delete(id);
            return Map.of("ok", true);
        });
    }

    private ResponseEntity<?> ok(Action action) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @FunctionalInterface
    private interface Action {
        Object run();
    }
}
