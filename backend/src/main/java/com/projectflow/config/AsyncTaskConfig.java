package com.projectflow.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncTaskConfig {
    @Bean(name = "taskExecutor")
    Executor taskExecutor(
        @Value("${projectflow.jobs.core-pool-size:2}") int corePoolSize,
        @Value("${projectflow.jobs.max-pool-size:4}") int maxPoolSize,
        @Value("${projectflow.jobs.queue-capacity:16}") int queueCapacity,
        @Value("${projectflow.jobs.shutdown-wait-seconds:30}") int shutdownWaitSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("project-analysis-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 服务关闭最多等待 30 秒，之后由持久化恢复语义接管。
        executor.setAwaitTerminationSeconds(shutdownWaitSeconds);
        executor.initialize();
        return executor;
    }
}
