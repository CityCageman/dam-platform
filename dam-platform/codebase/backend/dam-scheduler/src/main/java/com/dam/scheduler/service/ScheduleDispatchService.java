package com.dam.scheduler.service;

import com.dam.scheduler.client.TaskCenterClient;
import com.dam.scheduler.entity.ScheduleJob;
import com.dam.scheduler.entity.ScheduleRun;
import com.dam.scheduler.repository.ScheduleJobMapper;
import com.dam.scheduler.repository.ScheduleRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 调度任务 Cron 自动派发服务。
 * <p>
 * 周期性扫描 {@code status=ENABLED} 的调度任务，按
 * {@code CronExpression.next(lastDispatchedAt != null ? lastDispatchedAt : createdAt)}
 * 判断是否到点；到点后只插入一条 {@code PENDING} 运行记录并更新 {@code last_dispatched_at}。
 * <p>
 * 本服务只负责“派发运行实例”：写入 PENDING 运行记录后，在派发事务提交后提交
 * {@link ScheduleRunExecutor} 异步执行关联 DAG。
 * 多实例部署时依赖 PostgreSQL 行锁 {@code FOR UPDATE SKIP LOCKED} 保证同一个任务不会被
 * 重复派发；启动时只派发当前 due 的单次运行，不补发历史错过周期。
 *
 * @author DAM Platform
 */
@Service
@Slf4j
public class ScheduleDispatchService {

    /** 调度任务 Mapper */
    private final ScheduleJobMapper jobMapper;

    /** 运行记录 Mapper */
    private final ScheduleRunMapper runMapper;

    /** 单任务派发事务模板：锁定任务、写运行记录、更新派发时间在同一事务内完成 */
    private final TransactionTemplate transactionTemplate;

    /** TaskCenter 登记客户端，派发事务提交后异步登记运行实例 */
    private final TaskCenterClient taskCenterClient;

    /** DAG 执行器，派发事务提交后提交执行 */
    private final ScheduleRunExecutor runExecutor;

    /** 自动派发开关，false 时仅停止定时扫描（手动扫描接口仍可显式执行） */
    private final boolean dispatchEnabled;

    /** 单实例内避免定时扫描与手动扫描并发执行 */
    private final ReentrantLock scanLock = new ReentrantLock();

    /** 最近一次扫描时间 */
    private final AtomicReference<LocalDateTime> lastScanAt = new AtomicReference<>();

    /** 最近一次扫描派发数量 */
    private final AtomicInteger lastDispatchedCount = new AtomicInteger();

    /**
     * 构造自动派发服务。
     *
     * @param jobMapper          调度任务 Mapper
     * @param runMapper          运行记录 Mapper
     * @param transactionManager 事务管理器
     * @param taskCenterClient   TaskCenter 登记客户端
     * @param runExecutor        DAG 执行器
     * @param dispatchEnabled    是否启用定时自动派发
     */
    public ScheduleDispatchService(ScheduleJobMapper jobMapper,
                                   ScheduleRunMapper runMapper,
                                   PlatformTransactionManager transactionManager,
                                   TaskCenterClient taskCenterClient,
                                   ScheduleRunExecutor runExecutor,
                                   @Value("${dam.scheduler.dispatch-enabled:true}") boolean dispatchEnabled) {
        this.jobMapper = jobMapper;
        this.runMapper = runMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.taskCenterClient = taskCenterClient;
        this.runExecutor = runExecutor;
        this.dispatchEnabled = dispatchEnabled;
    }

    /**
     * 定时扫描入口。
     * <p>
     * 使用固定延迟（fixedDelay）保证上一次扫描结束后再等待间隔，避免扫描任务堆叠；
     * 扫描异常只记录日志，不让调度线程退出。
     */
    @Scheduled(fixedDelayString = "${dam.scheduler.dispatch-interval-ms:15000}")
    public void scheduledDispatch() {
        if (!dispatchEnabled) {
            return;
        }
        try {
            int dispatched = scanOnce();
            if (dispatched > 0) {
                log.info("Cron 自动派发扫描完成: dispatchedCount={}", dispatched);
            }
        } catch (Exception e) {
            // 单次扫描失败不能终止 Spring 调度线程，下一周期继续重试
            log.error("Cron 自动派发扫描异常，等待下一周期重试", e);
        }
    }

    /**
     * 执行一次派发扫描。
     * <p>
     * 候选任务先普通查询，只有真正到点的任务才在短事务内通过
     * {@code FOR UPDATE SKIP LOCKED} 加锁并重新校验，避免长时间锁定全部任务。
     * 单个任务异常只跳过该任务，不影响本次扫描中的其他任务。
     *
     * @return 本次成功派发的运行记录数
     */
    public int scanOnce() {
        scanLock.lock();
        int dispatched = 0;
        try {
            List<ScheduleJob> candidates = jobMapper.selectEnabledJobs();
            LocalDateTime now = LocalDateTime.now();
            for (ScheduleJob candidate : candidates) {
                Long jobId = candidate.getId();
                if (jobId == null || !isDue(candidate, now)) {
                    continue;
                }
                try {
                    if (dispatchIfDue(jobId)) {
                        dispatched++;
                    }
                } catch (Exception e) {
                    log.error("调度任务[{}]自动派发失败，跳过该任务: {}", jobId, e.getMessage(), e);
                }
            }
            return dispatched;
        } finally {
            // 即使本次扫描异常，也记录扫描时间与已派发数量，便于状态接口定位问题
            lastDispatchedCount.set(dispatched);
            lastScanAt.set(LocalDateTime.now());
            scanLock.unlock();
        }
    }

    /**
     * 查询自动派发状态。
     *
     * @return enabled/lastScanAt/lastDispatchedCount
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", dispatchEnabled);
        status.put("lastScanAt", lastScanAt.get());
        status.put("lastDispatchedCount", lastDispatchedCount.get());
        return status;
    }

    /**
     * 基于候选快照预判是否到点，避免每个扫描周期都锁定全部启用任务。
     * <p>
     * 这里使用普通查询得到的 {@code last_dispatched_at/created_at} 做一次无锁预判；
     * 只有预判到点才进入事务重新加锁并以行内最新值复核，因此预判偏旧也不会造成重复派发。
     *
     * @param job 候选任务快照
     * @param now 本次扫描时间
     * @return true 表示需要进入加锁派发流程
     */
    private boolean isDue(ScheduleJob job, LocalDateTime now) {
        LocalDateTime base = job.getLastDispatchedAt() != null
                ? job.getLastDispatchedAt() : job.getCreatedAt();
        if (base == null) {
            return false;
        }
        try {
            CronExpression cron = CronExpression.parse(job.getCronExpression());
            LocalDateTime next = cron.next(base);
            return next != null && !next.isAfter(now);
        } catch (IllegalArgumentException e) {
            log.warn("调度任务[{}] Cron 表达式非法，跳过自动派发: cron={}",
                    job.getId(), job.getCronExpression());
            return false;
        }
    }

    /**
     * 在独立事务内锁定单个任务，判断是否到点并派发。
     * <p>
     * 重新读取行锁内的最新 {@code last_dispatched_at} 是关键：其他实例可能已经派发并提交，
     * 本实例拿到锁后会基于新值计算下一次触发点，自然跳过重复派发。
     * <p>
     * TaskCenter 登记与 DAG 执行均安排在事务提交之后：只有运行记录与
     * {@code last_dispatched_at} 都成功提交才登记/执行，失败也不会回滚派发。
     *
     * @param jobId 任务ID
     * @return true 表示本次插入了一条 PENDING 运行记录
     */
    private boolean dispatchIfDue(Long jobId) {
        Boolean dispatched = transactionTemplate.execute(status -> {
            ScheduleJob job = jobMapper.selectEnabledJobForUpdate(jobId);
            if (job == null) {
                // 任务已删除、已停用，或正被其他实例锁定
                return false;
            }

            LocalDateTime base = job.getLastDispatchedAt() != null
                    ? job.getLastDispatchedAt() : job.getCreatedAt();
            if (base == null) {
                return false;
            }

            CronExpression cron;
            try {
                cron = CronExpression.parse(job.getCronExpression());
            } catch (IllegalArgumentException e) {
                log.warn("调度任务[{}] Cron 表达式非法，跳过自动派发: cron={}",
                        jobId, job.getCronExpression());
                return false;
            }

            LocalDateTime next = cron.next(base);
            LocalDateTime now = LocalDateTime.now();
            if (next == null || next.isAfter(now)) {
                return false;
            }

            ScheduleRun run = new ScheduleRun();
            run.setJobId(job.getId());
            run.setRunStatus("PENDING");
            run.setCreatedAt(now);
            runMapper.insert(run);

            int updated = jobMapper.updateLastDispatchedAt(job.getId(), now);
            if (updated != 1) {
                // 状态在锁定期间被并发修改时回滚运行记录，避免出现无法追踪的派发
                throw new IllegalStateException("更新 last_dispatched_at 失败: jobId=" + job.getId());
            }

            registerToTaskCenterAfterCommit(job.getJobCode(), job.getJobName(), run.getId());
            // 与 TaskCenter 登记一样在事务提交后提交，确保执行器能读到已提交的 PENDING 记录
            runExecutor.submitAfterCommit(run.getId(), job.getId());

            log.info("调度任务自动派发成功: jobId={}, jobCode={}, nextFireTime={}, runId={}",
                    job.getId(), job.getJobCode(), next, run.getId());
            return true;
        });
        return Boolean.TRUE.equals(dispatched);
    }

    /**
     * 在当前派发事务提交后异步登记 TaskCenter。
     * <p>
     * 事务回滚（例如更新 last_dispatched_at 失败）时不会触发 afterCommit，
     * 因此不会产生 TaskCenter 有实例而本地无运行记录的孤儿数据。
     * 登记动作本身已由客户端内部捕获异常并 warn，不影响派发与事务提交。
     *
     * @param jobCode 调度任务编码
     * @param jobName 调度任务名称
     * @param runId   已写入的运行记录ID
     */
    private void registerToTaskCenterAfterCommit(String jobCode, String jobName, Long runId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            taskCenterClient.registerStartAsync(jobCode, jobName, runId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    taskCenterClient.registerStartAsync(jobCode, jobName, runId);
                } catch (Exception e) {
                    // 双重兜底：登记失败绝不能影响已提交的派发或调度线程
                    log.warn("Cron 派发登记 TaskCenter 失败，不影响调度派发: jobCode={}, runId={}, error={}",
                            jobCode, runId, e.toString());
                }
            }
        });
    }
}
