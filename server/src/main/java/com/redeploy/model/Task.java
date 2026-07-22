package com.redeploy.model;

import java.time.LocalDateTime;

public class Task {

    private Long id;
    private String name;
    private String description;
    private String taskType;
    private Long groupId;
    private String deployPath;
    private String beforeCommand;
    private String afterCommand;
    private String stepsDefinition;
    private Boolean jenkinsEnabled;
    private String jenkinsUrl;
    private String jenkinsJobName;
    private String jenkinsArtifactPath;
    private String jenkinsUser;
    private String jenkinsToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Task() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getDeployPath() { return deployPath; }
    public void setDeployPath(String deployPath) { this.deployPath = deployPath; }

    public String getBeforeCommand() { return beforeCommand; }
    public void setBeforeCommand(String beforeCommand) { this.beforeCommand = beforeCommand; }

    public String getAfterCommand() { return afterCommand; }
    public void setAfterCommand(String afterCommand) { this.afterCommand = afterCommand; }

    public String getStepsDefinition() { return stepsDefinition; }
    public void setStepsDefinition(String stepsDefinition) { this.stepsDefinition = stepsDefinition; }

    public Boolean getJenkinsEnabled() { return jenkinsEnabled; }
    public void setJenkinsEnabled(Boolean jenkinsEnabled) { this.jenkinsEnabled = jenkinsEnabled; }

    public String getJenkinsUrl() { return jenkinsUrl; }
    public void setJenkinsUrl(String jenkinsUrl) { this.jenkinsUrl = jenkinsUrl; }

    public String getJenkinsJobName() { return jenkinsJobName; }
    public void setJenkinsJobName(String jenkinsJobName) { this.jenkinsJobName = jenkinsJobName; }

    public String getJenkinsArtifactPath() { return jenkinsArtifactPath; }
    public void setJenkinsArtifactPath(String jenkinsArtifactPath) { this.jenkinsArtifactPath = jenkinsArtifactPath; }

    public String getJenkinsUser() { return jenkinsUser; }
    public void setJenkinsUser(String jenkinsUser) { this.jenkinsUser = jenkinsUser; }

    public String getJenkinsToken() { return jenkinsToken; }
    public void setJenkinsToken(String jenkinsToken) { this.jenkinsToken = jenkinsToken; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
