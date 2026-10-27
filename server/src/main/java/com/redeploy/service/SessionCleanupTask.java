package com.redeploy.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 过期会话在请求时就会拒绝，这里定期把过期记录清掉。 */
@Component
public class SessionCleanupTask {

    private final AuthService authService;

    public SessionCleanupTask(AuthService authService) {
        this.authService = authService;
    }

    @Scheduled(cron = "0 15 * * * ?")
    public void purge() {
        authService.purgeExpired();
    }
}
