package com.redeploy.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class NotificationAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationAsyncConfig.class);

    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(16);
        exec.setQueueCapacity(256);
        exec.setThreadNamePrefix("notify-");
        exec.setRejectedExecutionHandler((r, e) -> {
            log.warn("通知线程池队列已满，丢弃任务");
        });
        exec.initialize();
        return exec;
    }
}
