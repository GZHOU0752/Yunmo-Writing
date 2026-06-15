package com.yunmo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置 — 用于 JPA 和 LLM 调用的 boundedElastic 隔离
 * 注意：此线程池只应在非事件循环线程触发，确保阻塞调用不会饥饿 Reactor 调度器
 */
@Configuration
public class AsyncConfig {

    @Bean("boundedElasticExecutor")
    public Executor boundedElasticExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        // 队列容量控制在合理范围，避免任务长时间排队
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("yunmo-");
        // AbortPolicy: 线程池满时抛异常，由上层 Reactor 超时/重试机制处理，
        // 避免 CallerRunsPolicy 在 Netty event-loop 上执行阻塞调用
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 优雅关闭等待 120 秒，确保进行中的 LLM 调用能完成
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
