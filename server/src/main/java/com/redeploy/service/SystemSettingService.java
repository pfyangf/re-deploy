package com.redeploy.service;

import com.redeploy.model.SystemSetting;
import com.redeploy.repository.SystemSettingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 系统设置：运行时可改的全局参数。
 * 当前唯一设置项 concurrent_deploy_limit = 同时执行的部署数（默认 2，1–20），
 * 超出的部署在 deployExecutor 无界队列中排队；修改后立即动态调整线程池大小。
 */
@Service
@Order(20)
public class SystemSettingService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingService.class);

    public static final String KEY_CONCURRENT_DEPLOY_LIMIT = "concurrent_deploy_limit";
    public static final int DEFAULT_CONCURRENT_DEPLOY_LIMIT = 2;
    public static final int MIN_CONCURRENT_DEPLOY_LIMIT = 1;
    public static final int MAX_CONCURRENT_DEPLOY_LIMIT = 20;

    private final SystemSettingMapper settingMapper;
    private final ThreadPoolTaskExecutor deployExecutor;

    private volatile int concurrentDeployLimit = DEFAULT_CONCURRENT_DEPLOY_LIMIT;

    public SystemSettingService(SystemSettingMapper settingMapper,
                                @Qualifier("deployExecutor") ThreadPoolTaskExecutor deployExecutor) {
        this.settingMapper = settingMapper;
        this.deployExecutor = deployExecutor;
    }

    @Override
    public void run(String... args) {
        try {
            SystemSetting setting = settingMapper.findByKey(KEY_CONCURRENT_DEPLOY_LIMIT);
            if (setting != null && setting.getValue() != null) {
                int saved = Integer.parseInt(setting.getValue().trim());
                if (isValidConcurrency(saved)) {
                    concurrentDeployLimit = saved;
                    applyPoolSize(saved);
                    log.info("加载部署并发数设置: {}", saved);
                } else {
                    log.warn("忽略越界的部署并发数设置: {}，使用默认值 {}", saved, DEFAULT_CONCURRENT_DEPLOY_LIMIT);
                }
            }
        } catch (Exception e) {
            log.warn("加载系统设置失败，使用默认部署并发数 {}: {}", DEFAULT_CONCURRENT_DEPLOY_LIMIT, e.getMessage());
        }
    }

    public int getConcurrentDeployLimit() {
        return concurrentDeployLimit;
    }

    public void setConcurrentDeployLimit(int limit) {
        if (!isValidConcurrency(limit)) {
            throw new IllegalArgumentException(
                    "部署并发数必须在 " + MIN_CONCURRENT_DEPLOY_LIMIT + "–" + MAX_CONCURRENT_DEPLOY_LIMIT + " 之间");
        }
        settingMapper.upsert(KEY_CONCURRENT_DEPLOY_LIMIT, String.valueOf(limit));
        concurrentDeployLimit = limit;
        applyPoolSize(limit);
        log.info("部署并发数已调整为 {}", limit);
    }

    private static boolean isValidConcurrency(int value) {
        return value >= MIN_CONCURRENT_DEPLOY_LIMIT && value <= MAX_CONCURRENT_DEPLOY_LIMIT;
    }

    /**
     * 动态调整部署池：调大时排队任务立即获得新 worker；调小不中断运行中的任务，空闲后自然收缩。
     */
    private void applyPoolSize(int limit) {
        deployExecutor.setCorePoolSize(limit);
        deployExecutor.setMaxPoolSize(limit);
    }
}
