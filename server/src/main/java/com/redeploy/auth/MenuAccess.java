package com.redeploy.auth;

/**
 * 把接口前缀对应到菜单路径。未单独列出的 /api 只允许管理员。
 * 仪表盘、部署等页面会读别的模块的列表，这些只读接口允许多个菜单。
 */
public final class MenuAccess {
    private MenuAccess() {}

    public static String requirement(String method, String uri) {
        if (uri == null) return "ADMIN";
        if (uri.startsWith("/api/auth")) return null;
        if (uri.startsWith("/api/system/users")) return "/access/users";
        if ("GET".equals(method) && "/api/system/roles".equals(uri)) return "ANY:/access/users,/access/roles";
        if (uri.startsWith("/api/system/roles")) return "/access/roles";
        if ("GET".equals(method) && uri.startsWith("/api/system/menus")) return "ANY:/access/menus,/access/roles";
        if (uri.startsWith("/api/system/menus")) return "/access/menus";
        if (uri.startsWith("/api/system/api-keys")) return "/access/api-keys";
        if (uri.startsWith("/api/system")) return "ADMIN";

        if ("GET".equals(method) && "/api/groups".equals(uri)) {
            return "ANY:/dashboard,/groups,/servers,/tasks,/deploy,/downloads";
        }
        if (uri.startsWith("/api/groups")) return "/groups";

        if ("GET".equals(method) && (uri.equals("/api/tasks") || uri.matches("/api/tasks/\\d+"))) {
            return "ANY:/tasks,/deploy";
        }
        if (uri.startsWith("/api/tasks")) return "/tasks";

        if (uri.startsWith("/api/settings")) return "/settings";
        if (uri.startsWith("/api/notification")) return "/notifications";
        if ("GET".equals(method) && uri.startsWith("/api/artifacts")) return "ANY:/artifacts,/deploy";
        if (uri.startsWith("/api/artifacts")) return "/artifacts";

        if (uri.startsWith("/api/downloads")
                || uri.matches("/api/servers/\\d+/downloads")
                || uri.matches("/api/servers/\\d+/download")) {
            return "/downloads";
        }

        if ("GET".equals(method) && ("/api/servers".equals(uri) || uri.matches("/api/servers/\\d+"))) {
            return "ANY:/dashboard,/servers,/groups,/deploy,/downloads";
        }
        if (uri.startsWith("/api/servers")) return "/servers";

        if ("GET".equals(method) && (uri.equals("/api/deploy/history")
                || uri.matches("/api/deploy/\\d+/(detail|status)"))) {
            return "ANY:/dashboard,/history,/deploy";
        }
        if (uri.startsWith("/api/deploy")) return "/deploy";
        if (uri.startsWith("/api/agents")) return "/servers";
        return "ADMIN";
    }
}
