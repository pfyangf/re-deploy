package com.redeploy.service;

import com.redeploy.model.DeployHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @deprecated 钉钉告警实现已迁移到通知管理（notification-management-and-task-events 变更）。
 * 该类仅保留为空壳，旧调用方（无）不再使用；
 * 启动期 NotificationConfigMigrator 会从 yml 自动导入等价 notification_config。
 * 计划下一版本移除。
 */
@Service
@Deprecated
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    @Value("${redeploy.dingtalk.enabled:false}")
    private boolean dingtalkEnabled;

    public void sendFailureAlert(DeployHistory history, List<DeployService.DeployResult> results) {
        if (!dingtalkEnabled) {
            log.debug("[AlertService 已废弃] 当前走 notification-dispatcher 路径，老配置 yml 未启用");
        }
        // 实际发送由 NotificationDispatcher 接管（订阅 TASK_FAILED 事件）
    }

    public void sendSuccessAlert(DeployHistory history) {
        // 实际发送由 NotificationDispatcher 接管（订阅 TASK_SUCCESS 事件）
    }
}
