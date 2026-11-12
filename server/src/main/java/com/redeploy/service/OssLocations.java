package com.redeploy.service;

/**
 * 校验并规范化 OSS endpoint、bucket、对象路径。
 */
public final class OssLocations {

    private OssLocations() {}

    public static String endpoint(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Endpoint 不能为空");
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.length() > 300) {
            throw new IllegalArgumentException("Endpoint 过长");
        }
        return value;
    }

    public static String bucket(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")) {
            throw new IllegalArgumentException("Bucket 名称不合法");
        }
        return value;
    }

    /** 返回去掉首尾空白后的对象路径。必须指向文件，不能以 / 结尾。 */
    public static String objectKey(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.endsWith("/") || value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("对象路径必须指向文件");
        }
        if (value.length() > 1024) {
            throw new IllegalArgumentException("对象路径过长");
        }
        fileName(value);
        return value;
    }

    /** 对象路径的文件名，用于推送到目标机。 */
    public static String fileName(String objectKey) {
        String key = objectKey == null ? "" : objectKey.trim();
        int slash = key.lastIndexOf('/');
        String name = slash >= 0 ? key.substring(slash + 1) : key;
        name = name.replaceAll("[\\\\:*?\"<>|]", "_");
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("对象路径必须指向文件");
        }
        return name;
    }
}
