package com.redeploy.service;

import com.redeploy.repository.DeployHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    @Autowired
    private DeployHistoryMapper deployHistoryMapper;

    @Value("${redeploy.history-retention-days:7}")
    private int historyRetentionDays;

    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    public void cleanupOldHistory() {
        log.info("Starting history cleanup task...");

        LocalDateTime threshold = LocalDateTime.now().minusDays(historyRetentionDays);
        int deleted = deployHistoryMapper.deleteByCreatedAtBefore(threshold);

        if (deleted > 0) {
            log.info("Deleted {} old deployment records", deleted);
        } else {
            log.info("No old records to clean up");
        }
    }
}
