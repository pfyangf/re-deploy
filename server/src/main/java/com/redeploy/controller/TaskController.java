package com.redeploy.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redeploy.model.Group;
import com.redeploy.model.Task;
import com.redeploy.repository.GroupMapper;
import com.redeploy.repository.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private GroupMapper groupMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public List<Task> listTasks(@RequestParam(required = false) Long groupId) {
        if (groupId != null) {
            return taskMapper.findByGroupId(groupId);
        }
        return taskMapper.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTask(@PathVariable Long id) {
        return taskMapper.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Task createTask(@RequestBody Task task) {
        // Assign to default group if not specified
        if (task.getGroupId() == null) {
            Group defaultGroup = groupMapper.findByName("default").orElse(null);
            if (defaultGroup != null) {
                task.setGroupId(defaultGroup.getId());
            }
        }
        // For simple deploy type task, auto-build steps definition
        if ("deploy".equals(task.getTaskType()) && task.getStepsDefinition() == null) {
            List<Map<String, Object>> steps = new ArrayList<>();
            
            if (task.getBeforeCommand() != null && !task.getBeforeCommand().isEmpty()) {
                steps.add(Map.of(
                    "type", "shell",
                    "name", "前置命令",
                    "command", task.getBeforeCommand()
                ));
            }
            
            steps.add(Map.of(
                "type", "deploy",
                "name", "部署应用",
                "deployPath", task.getDeployPath() != null ? task.getDeployPath() : ""
            ));
            
            if (task.getAfterCommand() != null && !task.getAfterCommand().isEmpty()) {
                steps.add(Map.of(
                    "type", "shell",
                    "name", "后置命令",
                    "command", task.getAfterCommand()
                ));
            }
            
            try {
                task.setStepsDefinition(objectMapper.writeValueAsString(steps));
            } catch (JsonProcessingException e) {
                task.setStepsDefinition("[]");
            }
        }
        
        // For command type task without stepsDefinition
        if ("command".equals(task.getTaskType()) && task.getStepsDefinition() == null) {
            List<Map<String, Object>> steps = new ArrayList<>();
            steps.add(Map.of(
                "type", "shell",
                "name", "执行命令",
                "command", task.getBeforeCommand() != null ? task.getBeforeCommand() : ""
            ));
            try {
                task.setStepsDefinition(objectMapper.writeValueAsString(steps));
            } catch (JsonProcessingException e) {
                task.setStepsDefinition("[]");
            }
        }
        
        taskMapper.insert(task);
        return task;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task task) {
        return taskMapper.findById(id)
                .map(existing -> {
                    existing.setName(task.getName());
                    existing.setDescription(task.getDescription());
                    existing.setTaskType(task.getTaskType());
                    existing.setGroupId(task.getGroupId());
                    existing.setDeployPath(task.getDeployPath());
                    existing.setBeforeCommand(task.getBeforeCommand());
                    existing.setAfterCommand(task.getAfterCommand());
                    // Update Jenkins configuration fields
                    existing.setJenkinsEnabled(task.getJenkinsEnabled());
                    existing.setJenkinsUrl(task.getJenkinsUrl());
                    existing.setJenkinsJobName(task.getJenkinsJobName());
                    existing.setJenkinsArtifactPath(task.getJenkinsArtifactPath());
                    existing.setJenkinsUser(task.getJenkinsUser());
                    existing.setJenkinsToken(task.getJenkinsToken());
                    
                    // Rebuild steps definition if needed
                    if ("deploy".equals(task.getTaskType()) && task.getStepsDefinition() == null) {
                        List<Map<String, Object>> steps = new ArrayList<>();
                        
                        if (existing.getBeforeCommand() != null && !existing.getBeforeCommand().isEmpty()) {
                            steps.add(Map.of(
                                "type", "shell",
                                "name", "前置命令",
                                "command", existing.getBeforeCommand()
                            ));
                        }
                        
                        steps.add(Map.of(
                            "type", "deploy",
                            "name", "部署应用",
                            "deployPath", existing.getDeployPath() != null ? existing.getDeployPath() : ""
                        ));
                        
                        if (existing.getAfterCommand() != null && !existing.getAfterCommand().isEmpty()) {
                            steps.add(Map.of(
                                "type", "shell",
                                "name", "后置命令",
                                "command", existing.getAfterCommand()
                            ));
                        }
                        
                        try {
                            existing.setStepsDefinition(objectMapper.writeValueAsString(steps));
                        } catch (JsonProcessingException e) {
                            existing.setStepsDefinition("[]");
                        }
                    } else if ("command".equals(task.getTaskType()) && task.getStepsDefinition() == null) {
                        List<Map<String, Object>> steps = new ArrayList<>();
                        steps.add(Map.of(
                            "type", "shell",
                            "name", "执行命令",
                            "command", existing.getBeforeCommand() != null ? existing.getBeforeCommand() : ""
                        ));
                        try {
                            existing.setStepsDefinition(objectMapper.writeValueAsString(steps));
                        } catch (JsonProcessingException e) {
                            existing.setStepsDefinition("[]");
                        }
                    } else {
                        existing.setStepsDefinition(task.getStepsDefinition());
                    }
                    
                    taskMapper.update(existing);
                    return ResponseEntity.ok(existing);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        if (taskMapper.existsById(id)) {
            taskMapper.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
