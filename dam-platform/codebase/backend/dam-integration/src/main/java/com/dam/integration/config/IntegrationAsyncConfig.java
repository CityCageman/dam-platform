package com.dam.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 数据集成同步执行线程池
 * <p>
 * trigger 接口只负责创建 PENDING 运行记录，事务提交后把实际 JDBC 同步提交到本线程池，
 * 避免长耗时同步阻塞 HTTP 请求。线程池有界，队列满时由调用方将运行记录标记为 FAILED，
 * 不会静默丢弃任务。
 *
 * @author DAM Platform
 */
@Configuration
public class IntegrationAsyncConfig {

    /** 核心线程数：基础版允许最多 2 个同步任务常驻执行 */
    private static final int CORE_POOL_SIZE = 2;

    /** 最大线程数：突发时最多 4 个同步任务并行 */
    private static final int MAX_POOL_SIZE = 4;

    /** 等待队列长度，队列满时拒绝并记录失败 */
    private static final int QUEUE_CAPACITY = 100;

    /**
     * 创建同步执行线程池
     *
     * @return 名为 integrationTaskExecutor 的线程池
     */
    @Bean("integrationTaskExecutor")
    public ThreadPoolTaskExecutor integrationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("integration-run-");
        // 服务关闭时等待已受理的同步任务完成，避免运行记录长时间停留在 RUNNING
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
