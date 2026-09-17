package com.redeploy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 部署任务专用线程池：core=max=部署并发数（默认 2，可在系统设置中运行时调整），
 * 队列无界——超过并发数的部署在队列中排队等待，不会被拒绝。
 * 池大小由 SystemSettingService 在启动后及设置变更时动态调整。
 */
@Configuration
@EnableAsync
public class DeployAsyncConfig {

    public static final int DEFAULT_CONCURRENCY = 2;

    @Bean("deployExecutor")
    public ThreadPoolTaskExecutor deployExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(DEFAULT_CONCURRENCY);
        exec.setMaxPoolSize(DEFAULT_CONCURRENCY);
        // 无界队列：超出并发数的部署排队，而非拒绝
        exec.setQueueCapacity(Integer.MAX_VALUE);
        exec.setThreadNamePrefix("deploy-");
        exec.setRejectedExecutionHandler((r, e) ->
                org.slf4j.LoggerFactory.getLogger(DeployAsyncConfig.class)
                        .warn("部署线程池拒绝任务（理论上无界队列不应触发）"));
        exec.initialize();
        return exec;
    }
}
