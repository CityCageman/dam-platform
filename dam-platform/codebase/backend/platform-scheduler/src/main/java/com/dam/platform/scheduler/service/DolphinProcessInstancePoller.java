package com.dam.platform.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dam.platform.scheduler.client.DolphinExecutionState;
import com.dam.platform.scheduler.client.DolphinSchedulerClient;
import com.dam.platform.scheduler.client.TaskCenterClient;
import com.dam.platform.scheduler.config.DolphinSchedulerProperties;
import com.dam.platform.scheduler.dto.dolphin.DolphinProcessInstance;
import com.dam.platform.scheduler.dto.dolphin.DolphinProcessInstanceResult;
import com.dam.platform.scheduler.entity.ScheduleRun;
import com.dam.platform.scheduler.repository.ScheduleRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DolphinScheduler 运行实例状态轮询。
 * <p>
 * DOLPHIN 引擎的 run 由 {@link ScheduleRunExecutor} 触发 DS 后保持 RUNNING，并回填
 * {@code dolphin_process_instance_id}；本服务按 {@code dam.scheduler.dolphin.poll-interval-ms}
 * （默认 15000ms）扫描有界批次，查询 DS 流程实例状态并回写终态。
 * <p>
 * 多实例语义：扫描不加行锁，避免网络调用期间长期持锁；终态回写使用
 * {@code id + run_status=RUNNING + dolphin_process_instance_id} 条件 CAS，
 * 多个实例同时轮询时只有一个能把 RUNNING 推进到终态并触发 TaskCenter finish，
 * 其余实例更新 0 行后直接跳过，因此不会重复回调。
 * <p>
 * 轮询只使用配置中的 base-url/token/project-code；DS 未启用/未配置时静默跳过，
 * 不影响本地执行器与 Cron 派发。服务重启后已存在的 RUNNING+DOLPHIN 记录会被继续轮询；
 * 长期无法回收的记录由 {@link ScheduleRunExecutor#recoverStaleRuns()} 兜底。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DolphinProcessInstancePoller {

    /** 运行状态：执行中 */
    private static final String STATUS_RUNNING = "RUNNING";

    /** 单轮批量下限/上限，避免配置异常导致无界扫描 */
    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 200;

    /** 配置不完整只提示一次，避免每 15 秒刷日志 */
    private final AtomicBoolean configurationWarned = new AtomicBoolean(false);

    /** 轮询游标：批尾推进、扫尾归零，避免长期运行实例饿死后续记录 */
    private final AtomicLong cursor = new AtomicLong(0);

    private final DolphinSchedulerProperties properties;

    private final DolphinSchedulerClient dolphinClient;

    private final ScheduleRunMapper runMapper;

    private final TaskCenterClient taskCenterClient;

    /**
     * 定时轮询 DOLPHIN 引擎的 RUNNING 运行记录。
     * <p>
     * 使用 fixedDelay 保证上一轮结束后再等待间隔，避免任务堆叠；单条记录异常只记录
     * warn 并继续处理同批其他记录，不让调度线程退出。
     */
    @Scheduled(fixedDelayString = "${dam.scheduler.dolphin.poll-interval-ms:15000}",
            initialDelayString = "${dam.scheduler.dolphin.poll-interval-ms:15000}")
    public void pollRunningInstances() {
        if (!properties.isDolphinEnabled()) {
            return;
        }
        if (!dolphinClient.isConfigured()) {
            if (configurationWarned.compareAndSet(false, true)) {
                log.warn("DolphinScheduler 已启用但配置不完整，跳过状态轮询: {}",
                        String.join("; ", properties.configurationIssues()));
            }
            return;
        }
        configurationWarned.set(false);

        int batchSize = Math.clamp(properties.getDolphin().getPollBatchSize(),
                MIN_BATCH_SIZE, MAX_BATCH_SIZE);
        long afterId = cursor.get();
        List<ScheduleRun> runs;
        try {
            runs = runMapper.selectDolphinPollable(STATUS_RUNNING, afterId, batchSize);
        } catch (Exception e) {
            log.error("扫描 DolphinScheduler 待轮询运行记录异常: errorClass={}, error={}",
                    e.getClass().getSimpleName(), e.getMessage());
            return;
        }
        if (runs.isEmpty()) {
            // 已扫到尾部：下一轮从最小 ID 重新开始
            if (afterId != 0) {
                cursor.set(0);
            }
            return;
        }
        // 未取满说明已到尾部，下一轮归零；否则从批尾继续
        if (runs.size() < batchSize) {
            cursor.set(0);
        } else {
            cursor.set(runs.get(runs.size() - 1).getId());
        }

        int completed = 0;
        for (ScheduleRun run : runs) {
            try {
                if (pollOne(run)) {
                    completed++;
                }
            } catch (Exception e) {
                // 单条记录失败不阻塞同批其他记录；下一轮继续重试
                log.warn("轮询 DolphinScheduler 流程实例异常: runId={}, processInstanceId={}, "
                                + "errorClass={}, error={}",
                        run.getId(), run.getDolphinProcessInstanceId(),
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }
        if (completed > 0) {
            log.info("DolphinScheduler 运行终态回写完成: completedCount={}, scanned={}",
                    completed, runs.size());
        }
    }

    /**
     * 轮询单条运行记录。
     *
     * @param run 运行记录（必须已回填 processInstanceId）
     * @return true 表示本轮写入了终态
     */
    private boolean pollOne(ScheduleRun run) {
        Long processInstanceId = run.getDolphinProcessInstanceId();
        if (processInstanceId == null) {
            return false;
        }
        DolphinProcessInstanceResult result = dolphinClient.queryProcessInstance(processInstanceId);
        if (!result.ok() || result.instance() == null) {
            log.warn("查询 DolphinScheduler 流程实例失败，下一轮重试: runId={}, "
                            + "processInstanceId={}, message={}",
                    run.getId(), processInstanceId, result.message());
            return false;
        }

        DolphinProcessInstance instance = result.instance();
        Integer state = instance.state();
        String targetStatus = DolphinExecutionState.toLocalStatus(state);
        if (targetStatus == null) {
            // 排队/运行中/暂停等非终态，继续等待
            return false;
        }

        String stateName = instance.stateName() != null
                ? instance.stateName() : DolphinExecutionState.nameByCode(state);
        String error = "SUCCESS".equals(targetStatus) ? null
                : "DolphinScheduler 流程实例未成功结束: state=" + state
                + (stateName == null ? "" : "(" + stateName + ")");
        return finishRun(run, targetStatus, error);
    }

    /**
     * CAS 回写终态，并在更新成功后触发 TaskCenter finish。
     * <p>
     * 条件包含 {@code run_status=RUNNING} 与 {@code dolphin_process_instance_id}：
     * 已被其他实例回写、或运行记录被状态更新改写时更新 0 行，不重复回调 TaskCenter。
     *
     * @param run          运行记录
     * @param targetStatus SUCCESS/FAILED
     * @param error        失败原因，成功时为 null
     * @return true 表示本实例完成了终态回写
     */
    private boolean finishRun(ScheduleRun run, String targetStatus, String error) {
        String safeError = truncate(error);
        int updated = runMapper.update(null, new LambdaUpdateWrapper<ScheduleRun>()
                .eq(ScheduleRun::getId, run.getId())
                .eq(ScheduleRun::getRunStatus, STATUS_RUNNING)
                .eq(ScheduleRun::getDolphinProcessInstanceId, run.getDolphinProcessInstanceId())
                .set(ScheduleRun::getRunStatus, targetStatus)
                .set(ScheduleRun::getFinishedAt, LocalDateTime.now())
                .set(ScheduleRun::getErrorMessage, safeError));
        if (updated == 0) {
            log.debug("运行记录已被其他实例回写或状态已变化，跳过: runId={}, targetStatus={}",
                    run.getId(), targetStatus);
            return false;
        }
        log.info("DolphinScheduler 流程实例结束: runId={}, processInstanceId={}, status={}",
                run.getId(), run.getDolphinProcessInstanceId(), targetStatus);
        // TaskCenter 开始/结束沿用现有回调客户端；instanceId 为空（登记失败）时自动跳过
        taskCenterClient.finish(run.getTaskCenterInstanceId(), targetStatus, safeError, null);
        return true;
    }

    /**
     * 截断错误消息。
     *
     * @param message 原始消息
     * @return 不超过 2000 字符的消息
     */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) + "..." : message;
    }
}
