package com.dam.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** 连接测试/数据预览探测线程上限，避免慢数据库拖垮进程 */
    private static final int MAX_PROBE_THREADS = 8;

    /** 探测线程空闲存活时间（秒），线程为守护线程，不阻止 JVM 退出 */
    private static final long PROBE_THREAD_KEEP_ALIVE_SECONDS = 60L;

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

    /**
     * 连接测试/数据预览探测线程池
     * <p>
     * 每个探测任务在独立守护线程中执行，调用方用 Future 设置整体超时；
     * 数据库无响应时可通过取消 + JDBC socketTimeout 释放线程，避免阻塞 HTTP 线程。
     * 探测并发上限为 {@value #MAX_PROBE_THREADS}，超出时立即拒绝并由接口返回可读错误。
     *
     * @return 名为 integrationProbeExecutor 的探测线程池
     */
    @Bean(name = "integrationProbeExecutor", destroyMethod = "shutdownNow")
    public ExecutorService integrationProbeExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "integration-probe-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(0, MAX_PROBE_THREADS,
                PROBE_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<>(), threadFactory, new ThreadPoolExecutor.AbortPolicy());
    }
}
