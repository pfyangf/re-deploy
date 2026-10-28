package com.redeploy.auth;

/**
 * API 密钥可调用的接口。只读和新增，外加发起/取消部署。
 * 系统设置、用户角色菜单、密钥管理本身、改删和远程命令一律拒绝。
 */
public final class ApiKeyAccess {
    private ApiKeyAccess() {}

    public static boolean allows(String method, String uri) {
        if (method == null || uri == null) return false;
        String path = uri;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        if ("GET".equals(method) && "/api/auth/me".equals(path)) return true;
        if (path.startsWith("/api/settings") || path.startsWith("/api/system")) return false;
        if ("PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method)) return false;

        if ("GET".equals(method)) {
            return path.startsWith("/api/groups")
                    || path.startsWith("/api/servers")
                    || path.startsWith("/api/tasks")
                    || path.startsWith("/api/artifacts")
                    || path.startsWith("/api/deploy")
                    || path.startsWith("/api/notification")
                    || path.startsWith("/api/downloads")
                    || path.startsWith("/api/agents");
        }
        if (!"POST".equals(method)) return false;
        if ("/api/groups".equals(path) || "/api/servers".equals(path) || "/api/tasks".equals(path)) return true;
        if ("/api/notification/configs".equals(path)) return true;
        if ("/api/deploy".equals(path)) return true;
        return path.matches("/api/servers/\\d+/test")
                || path.matches("/api/servers/\\d+/download")
                || path.matches("/api/deploy/\\d+/cancel");
    }
}
