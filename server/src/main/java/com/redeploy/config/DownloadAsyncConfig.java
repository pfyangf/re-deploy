package com.redeploy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class DownloadAsyncConfig {

    @Bean("downloadExecutor")
    public Executor downloadExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        // 下载是 IO 密集型，给较多线程；队列缓冲避免突发请求被拒
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(16);
        exec.setQueueCapacity(64);
        exec.setThreadNamePrefix("download-");
        exec.setRejectedExecutionHandler((r, e) -> {
            org.slf4j.LoggerFactory.getLogger(DownloadAsyncConfig.class)
                    .warn("下载线程池队列已满，任务被拒绝");
        });
        exec.initialize();
        return exec;
    }
}
