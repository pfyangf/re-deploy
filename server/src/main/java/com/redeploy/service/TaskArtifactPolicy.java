package com.redeploy.service;

import com.redeploy.model.Task;

import java.util.function.LongPredicate;

/**
 * 任务构建物来源三选一：none、jenkins、oss。保存时同步 jenkinsEnabled，并清空未选中的 OSS 字段。
 */
public final class TaskArtifactPolicy {

    public static final String NONE = "none";
    public static final String JENKINS = "jenkins";
    public static final String OSS = "oss";

    private TaskArtifactPolicy() {}

    /** 部署时读取来源。未填写时，已启用 Jenkins 视为 jenkins。 */
    public static String resolve(Task task) {
        if (task == null) return NONE;
        String raw = task.getArtifactSource();
        if (raw != null && !raw.isBlank()) {
            String source = raw.trim().toLowerCase();
            if (NONE.equals(source) || JENKINS.equals(source) || OSS.equals(source)) {
                return source;
            }
        }
        return Boolean.TRUE.equals(task.getJenkinsEnabled()) ? JENKINS : NONE;
    }

    public static void apply(Task task, LongPredicate credentialExists) {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        String source = requestedSource(task);
        task.setArtifactSource(source);
        if (JENKINS.equals(source)) {
            task.setJenkinsEnabled(true);
            clearOss(task);
            return;
        }
        task.setJenkinsEnabled(false);
        if (NONE.equals(source)) {
            clearOss(task);
            return;
        }
        if (task.getOssCredentialId() == null || credentialExists == null
                || !credentialExists.test(task.getOssCredentialId())) {
            throw new IllegalArgumentException("请选择有效的 OSS 凭证");
        }
        task.setOssBucket(OssLocations.bucket(task.getOssBucket()));
        task.setOssObjectKey(OssLocations.objectKey(task.getOssObjectKey()));
    }

    private static String requestedSource(Task task) {
        String raw = task.getArtifactSource();
        if (raw == null || raw.isBlank()) {
            return Boolean.TRUE.equals(task.getJenkinsEnabled()) ? JENKINS : NONE;
        }
        String source = raw.trim().toLowerCase();
        if (!NONE.equals(source) && !JENKINS.equals(source) && !OSS.equals(source)) {
            throw new IllegalArgumentException("不支持的构建物来源");
        }
        return source;
    }

    private static void clearOss(Task task) {
        task.setOssCredentialId(null);
        task.setOssBucket(null);
        task.setOssObjectKey(null);
    }
}
