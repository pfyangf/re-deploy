package com.redeploy.controller;

import com.redeploy.auth.AuthPrincipal;
import com.redeploy.config.AuthInterceptor;
import com.redeploy.service.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class RbacController {

    private final RbacService rbacService;

    public RbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/users")
    public Object listUsers() {
        return rbacService.listUsers();
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        return ok(() -> rbacService.createUser(body));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ok(() -> rbacService.updateUser(id, body, currentUserId(request)));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        return ok(() -> {
            rbacService.deleteUser(id, currentUserId(request));
            return Map.of("ok", true);
        });
    }

    @GetMapping("/roles")
    public Object listRoles() {
        return rbacService.listRoles();
    }

    @PostMapping("/roles")
    public ResponseEntity<?> createRole(@RequestBody Map<String, Object> body) {
        return ok(() -> rbacService.createRole(body));
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ok(() -> rbacService.updateRole(id, body));
    }

    @PutMapping("/roles/{id}/menus")
    public ResponseEntity<?> assignMenus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ok(() -> rbacService.assignRoleMenus(id, longIds(body.get("menuIds"))));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<?> deleteRole(@PathVariable Long id) {
        return ok(() -> {
            rbacService.deleteRole(id);
            return Map.of("ok", true);
        });
    }

    @GetMapping("/menus")
    public Object listMenus() {
        return rbacService.listMenus();
    }

    @PostMapping("/menus")
    public ResponseEntity<?> createMenu(@RequestBody Map<String, Object> body) {
        return ok(() -> rbacService.createMenu(body));
    }

    @PutMapping("/menus/{id}")
    public ResponseEntity<?> updateMenu(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ok(() -> rbacService.updateMenu(id, body));
    }

    @DeleteMapping("/menus/{id}")
    public ResponseEntity<?> deleteMenu(@PathVariable Long id) {
        return ok(() -> {
            rbacService.deleteMenu(id);
            return Map.of("ok", true);
        });
    }

    private Long currentUserId(HttpServletRequest request) {
        Object principal = request.getAttribute(AuthInterceptor.ATTR);
        if (principal instanceof AuthPrincipal auth) return auth.getUserId();
        return null;
    }

    private List<Long> longIds(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().map(item -> Long.parseLong(String.valueOf(item))).toList();
    }

    private ResponseEntity<?> ok(Action action) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (NumberFormatException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "参数格式不正确");
            return ResponseEntity.badRequest().body(error);
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
