package com.redeploy.service;

import com.redeploy.model.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskArtifactPolicyTest {

    @Test
    void blankSourceFollowsJenkinsFlag() {
        Task task = new Task();
        task.setJenkinsEnabled(true);
        task.setOssBucket("should-clear");
        TaskArtifactPolicy.apply(task, id -> true);
        assertEquals(TaskArtifactPolicy.JENKINS, task.getArtifactSource());
        assertTrue(task.getJenkinsEnabled());
        assertNull(task.getOssBucket());
    }

    @Test
    void ossRequiresExistingCredentialAndClearsJenkinsFlag() {
        Task task = ossTask();
        TaskArtifactPolicy.apply(task, id -> id == 7L);
        assertEquals(TaskArtifactPolicy.OSS, task.getArtifactSource());
        assertFalse(task.getJenkinsEnabled());
        assertEquals("my-bucket", task.getOssBucket());
        assertEquals("release/app.jar", task.getOssObjectKey());
    }

    @Test
    void ossRejectsUnknownCredential() {
        Task task = ossTask();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TaskArtifactPolicy.apply(task, id -> false));
        assertEquals("请选择有效的 OSS 凭证", error.getMessage());
    }

    @Test
    void noneClearsOssFields() {
        Task task = ossTask();
        task.setArtifactSource("none");
        TaskArtifactPolicy.apply(task, id -> true);
        assertEquals(TaskArtifactPolicy.NONE, task.getArtifactSource());
        assertFalse(task.getJenkinsEnabled());
        assertNull(task.getOssCredentialId());
    }

    @Test
    void resolvePrefersExplicitSource() {
        Task task = new Task();
        task.setJenkinsEnabled(true);
        task.setArtifactSource("oss");
        assertEquals(TaskArtifactPolicy.OSS, TaskArtifactPolicy.resolve(task));
    }

    @Test
    void objectFileNameKeepsDashes() {
        assertEquals("app-1.0.jar", OssLocations.fileName("release/app-1.0.jar"));
        assertEquals("https://oss-cn-hangzhou.aliyuncs.com",
                OssLocations.endpoint("oss-cn-hangzhou.aliyuncs.com"));
        assertThrows(IllegalArgumentException.class, () -> OssLocations.bucket("Bad_Bucket"));
        assertThrows(IllegalArgumentException.class, () -> OssLocations.objectKey("release/"));
    }

    private static Task ossTask() {
        Task task = new Task();
        task.setArtifactSource("oss");
        task.setJenkinsEnabled(true);
        task.setOssCredentialId(7L);
        task.setOssBucket(" my-bucket ");
        task.setOssObjectKey(" release/app.jar ");
        return task;
    }
}
