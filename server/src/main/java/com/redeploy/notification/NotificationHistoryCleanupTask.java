package com.redeploy.notification;

import com.redeploy.repository.NotificationHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 每天凌晨 03:30 清理 30 天前的通知历史。
 * 周期可由 redeploy.notification.history-retention-days 调整。
 */
@Component
public class NotificationHistoryCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(NotificationHistoryCleanupTask.class);

    private final NotificationHistoryMapper historyMapper;
    private final int retentionDays;

    public NotificationHistoryCleanupTask(NotificationHistoryMapper historyMapper,
                                            @Value("${redeploy.notification.history-retention-days:30}") int retentionDays) {
        this.historyMapper = historyMapper;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanup() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            int deleted = historyMapper.deleteBefore(cutoff);
            log.info("通知历史清理完成: 删除 {} 条（cutoff={}, retention={}d）", deleted, cutoff, retentionDays);
        } catch (Exception e) {
            log.error("通知历史清理失败", e);
        }
    }
}
