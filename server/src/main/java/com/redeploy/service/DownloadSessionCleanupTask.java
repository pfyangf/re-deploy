package com.redeploy.service;

import com.redeploy.model.DownloadSession;
import com.redeploy.repository.DownloadSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * 远端文件下载清理：
 *  - 启动期（10 分钟一次轮询）扫 stale downloading 标 failed（崩溃恢复）
 *  - 每天 04:00 清理 30 天前 SUCCESS 记录 + 落盘文件
 */
@Component
public class DownloadSessionCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(DownloadSessionCleanupTask.class);

    private final DownloadSessionMapper sessionMapper;
    private final String downloadDir;
    private final int retentionDays;

    public DownloadSessionCleanupTask(DownloadSessionMapper sessionMapper,
                                       @Value("${redeploy.download.dir:./data/downloads}") String downloadDir,
                                       @Value("${redeploy.download.retention-days:30}") int retentionDays) {
        this.sessionMapper = sessionMapper;
        this.downloadDir = downloadDir;
        this.retentionDays = retentionDays;
    }

    /**
     * 启动期 + 10 分钟兜底：把卡在 downloading 状态超过 1 小时的 session 标 failed。
     * 避免 agent 崩了 server 不知道，session 永远不结束。
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 600_000)
    public void markStaleAsFailed() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
            int n = sessionMapper.markStaleDownloadingAsFailed(cutoff);
            if (n > 0) {
                log.warn("已把 {} 个 stale downloading session 标为 FAILED（updated_at < {}）", n, cutoff);
            }
        } catch (Exception e) {
            log.error("扫描 stale downloading session 失败", e);
        }
    }

    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanupOld() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            // 先查要删的记录拿到 local_path，再删 DB
            var stale = sessionMapper.listByFilter(null, DownloadSession.STATUS_SUCCESS, 500, 0);
            int deletedFiles = 0;
            for (var s : stale) {
                if (s.getCreatedAt() == null || !s.getCreatedAt().isBefore(cutoff)) continue;
                if (s.getLocalPath() != null) {
                    try {
                        Path p = Paths.get(s.getLocalPath());
                        if (Files.deleteIfExists(p)) deletedFiles++;
                    } catch (Exception e) {
                        log.warn("删除旧文件失败: {}", s.getLocalPath(), e);
                    }
                }
            }
            int deleted = sessionMapper.deleteSuccessOlderThan(cutoff);
            log.info("下载会话清理完成: 删除 {} 条记录 / {} 个文件（cutoff={}, retention={}d）",
                    deleted, deletedFiles, cutoff, retentionDays);
        } catch (Exception e) {
            log.error("下载会话清理失败", e);
        }
    }
}
