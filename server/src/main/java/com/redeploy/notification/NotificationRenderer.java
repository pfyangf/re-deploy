package com.redeploy.notification;

import com.redeploy.model.DeployHistory;
import com.redeploy.model.Server;
import com.redeploy.model.SystemEvent;
import com.redeploy.model.Task;

import java.time.Duration;
import java.util.Map;

/**
 * 把 SystemEvent 渲染成可投递的 NotificationMessage。
 * 模板集中在这里，避免 channel 实现各自渲染风格不一致。
 */
public final class NotificationRenderer {

    private NotificationRenderer() {}

    public static NotificationMessage render(SystemEvent event) {
        if (event == null || event.getType() == null) {
            return new NotificationMessage("ReDeploy 通知", "");
        }
        return switch (event.getType()) {
            case SystemEvent.TYPE_TASK_STARTED -> renderTaskStarted(event);
            case SystemEvent.TYPE_TASK_SUCCESS -> renderTaskSuccess(event);
            case SystemEvent.TYPE_TASK_FAILED -> renderTaskFailed(event);
            case SystemEvent.TYPE_TASK_CANCELLED -> renderTaskCancelled(event);
            case SystemEvent.TYPE_STEP_FAILED -> renderStepFailed(event);
            case SystemEvent.TYPE_STEP_TIMEOUT -> renderStepTimeout(event);
            case SystemEvent.TYPE_AGENT_ONLINE -> renderAgentOnline(event);
            case SystemEvent.TYPE_AGENT_OFFLINE -> renderAgentOffline(event);
            default -> new NotificationMessage(
                    "[" + event.getType() + "] " + titleOf(event),
                    bodyOf(event));
        };
    }

    private static NotificationMessage renderTaskStarted(SystemEvent event) {
        Task t = event.getTask();
        DeployHistory h = event.getHistory();
        String body = """
                **任务名称**: %s
                **部署记录ID**: %s
                **版本**: %s
                **开始时间**: %s
                **服务器数**: %d
                """.formatted(
                t != null ? t.getName() : "(未知)",
                h != null ? h.getId() : "(无)",
                h != null && h.getVersion() != null ? h.getVersion() : "(无)",
                h != null && h.getStartedAt() != null ? h.getStartedAt().toString() : "(无)",
                event.getServerIds() != null ? event.getServerIds().size() : 0);
        return new NotificationMessage("▶ 部署任务开始", body);
    }

    private static NotificationMessage renderTaskSuccess(SystemEvent event) {
        DeployHistory h = event.getHistory();
        Task t = event.getTask();
        String body = """
                **任务名称**: %s
                **部署记录ID**: %s
                **版本**: %s
                **完成时间**: %s
                **总耗时**: %s
                """.formatted(
                t != null ? t.getName() : "(未知)",
                h != null ? h.getId() : "(无)",
                h != null && h.getVersion() != null ? h.getVersion() : "(无)",
                h != null && h.getCompletedAt() != null ? h.getCompletedAt().toString() : "(无)",
                formatDuration(h));
        return new NotificationMessage("✅ 部署成功", body);
    }

    private static NotificationMessage renderTaskFailed(SystemEvent event) {
        DeployHistory h = event.getHistory();
        Task t = event.getTask();
        StringBuilder body = new StringBuilder();
        body.append("**任务名称**: ").append(t != null ? t.getName() : "(未知)").append("\n");
        body.append("**部署记录ID**: ").append(h != null ? h.getId() : "(无)").append("\n");
        body.append("**版本**: ").append(h != null && h.getVersion() != null ? h.getVersion() : "(无)").append("\n");
        if (h != null && h.getErrorMessage() != null) {
            body.append("**错误**: ").append(truncate(h.getErrorMessage(), 500)).append("\n");
        }
        Map<String, Boolean> results = event.getServerResults();
        if (results != null && !results.isEmpty()) {
            body.append("\n### 服务器详情\n");
            for (Map.Entry<String, Boolean> en : results.entrySet()) {
                body.append("- ").append(en.getKey()).append(": ")
                        .append(Boolean.TRUE.equals(en.getValue()) ? "✓" : "✗").append("\n");
            }
        }
        return new NotificationMessage("❌ 部署失败", body.toString());
    }

    private static NotificationMessage renderTaskCancelled(SystemEvent event) {
        DeployHistory h = event.getHistory();
        return new NotificationMessage("⚠️ 部署已取消",
                "**部署记录ID**: " + (h != null ? h.getId() : "(无)") +
                        "\n**原因**: " + titleOf(event));
    }

    private static NotificationMessage renderStepFailed(SystemEvent event) {
        return new NotificationMessage("⚠️ 步骤执行失败",
                "**任务ID**: " + event.getTaskId() +
                        "\n**步骤**: " + event.getStepName() +
                        "\n**错误**: " + titleOf(event));
    }

    private static NotificationMessage renderStepTimeout(SystemEvent event) {
        return new NotificationMessage("⏱️ 步骤执行超时",
                "**任务ID**: " + event.getTaskId() +
                        "\n**步骤**: " + event.getStepName() +
                        "\n**超时**: " + titleOf(event));
    }

    private static NotificationMessage renderAgentOnline(SystemEvent event) {
        Server s = event.getServer();
        return new NotificationMessage("🟢 Agent 上线",
                "**服务器**: " + serverDesc(s));
    }

    private static NotificationMessage renderAgentOffline(SystemEvent event) {
        Server s = event.getServer();
        return new NotificationMessage("🔴 Agent 离线",
                "**服务器**: " + serverDesc(s) + "\n**原因**: " + titleOf(event));
    }

    private static String serverDesc(Server s) {
        if (s == null) return "(未知)";
        String name = s.getName() != null ? s.getName() : "(未知)";
        return name + " (" + s.getHost() + ":" + s.getPort() + ")";
    }

    /** 由 startedAt/completedAt 计算耗时，缺字段返回 (无)。 */
    private static String formatDuration(DeployHistory h) {
        if (h == null || h.getStartedAt() == null || h.getCompletedAt() == null) {
            return "(无)";
        }
        Duration d = Duration.between(h.getStartedAt(), h.getCompletedAt());
        long secs = d.getSeconds();
        if (secs < 60) return secs + "s";
        return (secs / 60) + "m" + (secs % 60) + "s";
    }

    private static String titleOf(SystemEvent event) {
        return event.getReason() != null ? event.getReason() : "";
    }

    private static String bodyOf(SystemEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.getTaskId() != null) sb.append("**任务ID**: ").append(event.getTaskId()).append("\n");
        if (event.getReason() != null) sb.append("**说明**: ").append(event.getReason()).append("\n");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
