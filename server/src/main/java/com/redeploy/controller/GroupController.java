package com.redeploy.controller;

import com.redeploy.model.Group;
import com.redeploy.repository.GroupMapper;
import com.redeploy.repository.ServerMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupMapper groupMapper;
    private final ServerMapper serverMapper;

    public GroupController(GroupMapper groupMapper, ServerMapper serverMapper) {
        this.groupMapper = groupMapper;
        this.serverMapper = serverMapper;
    }

    @GetMapping
    public List<Group> list() {
        return groupMapper.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Group group) {
        if (group.getName() == null || group.getName().trim().isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "分组名称不能为空");
            return ResponseEntity.badRequest().body(error);
        }

        if (groupMapper.findByName(group.getName().trim()).isPresent()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "分组名称已存在");
            return ResponseEntity.badRequest().body(error);
        }

        group.setName(group.getName().trim());
        groupMapper.insert(group);
        return ResponseEntity.ok(group);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Group group) {
        if (!groupMapper.existsById(id)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "分组不存在");
            return ResponseEntity.notFound().build();
        }

        if (group.getName() == null || group.getName().trim().isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "分组名称不能为空");
            return ResponseEntity.badRequest().body(error);
        }

        // Check if new name conflicts with another group
        var existing = groupMapper.findByName(group.getName().trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "分组名称已存在");
            return ResponseEntity.badRequest().body(error);
        }

        group.setId(id);
        group.setName(group.getName().trim());
        groupMapper.update(group);
        return ResponseEntity.ok(group);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!groupMapper.existsById(id)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "分组不存在");
            return ResponseEntity.notFound().build();
        }

        // Check if any server uses this group
        long serverCount = serverMapper.countByGroupId(id);
        if (serverCount > 0) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "分组下还有服务器，无法删除");
            return ResponseEntity.badRequest().body(error);
        }

        groupMapper.deleteById(id);
        Map<String, Boolean> result = new HashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }
}
