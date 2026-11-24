package com.dam.platform.taskcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.vo.PageResult;
import com.dam.platform.taskcenter.dto.TaskInstanceCancelRequest;
import com.dam.platform.taskcenter.dto.TaskStatusRequest;
import com.dam.platform.taskcenter.entity.TaskDefinition;
import com.dam.platform.taskcenter.entity.TaskInstance;
import com.dam.platform.taskcenter.repository.TaskDefinitionMapper;
import com.dam.platform.taskcenter.repository.TaskInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 任务实例服务。
 * <p>
 * 负责手动触发任务、实例分页/详情查询与状态流转。触发只创建 PENDING 实例，
 * 不执行任务、不伪造 SUCCESS，返回 message=executor not implemented。
 * 状态机：PENDING → RUNNING/SUCCESS/FAILED/CANCELLED；
 * RUNNING → SUCCESS/FAILED/CANCELLED；终态不允许再流转。
 * <p>
 * <b>超时回收：</b>由 {@code @Scheduled} 周期扫描长时间停留在 PENDING/RUNNING 的实例
 * （执行器崩溃或未回填状态），PENDING 超时标记 CANCELLED、RUNNING 超时标记 FAILED，
 * 统一回填 finished_at 与 error_message“实例超时自动回收”，任何情况下都不会标记 SUCCESS。
 * 多实例部署通过 PostgreSQL 行锁 {@code FOR UPDATE SKIP LOCKED} + 带状态条件的
 * {@code UPDATE} 双重保护避免重复回收；单次扫描最多处理 100 条，整批在一个事务内提交，
 * 单条失败通过 SAVEPOINT 回滚该条并继续处理其余记录。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskInstanceService {

    /** 单页上限，避免 pageSize 过大拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 单次回收扫描/处理的最大实例条数（PENDING 与 RUNNING 合计） */
    private static final int RECOVER_BATCH_SIZE = 100;

    /** 启动后首次回收扫描的延迟（毫秒），固定 60 秒，避开启动期数据库连接竞争 */
    private static final long RECOVER_INITIAL_DELAY_MS = 60_000L;

    /** 超时回收写入的固定原因（task_instance.error_message 已存在字段） */
    private static final String RECOVER_ERROR_MESSAGE = "实例超时自动回收";

    /** PENDING 超时后的回收目标状态：取消 */
    private static final String RECOVER_PENDING_TARGET = "CANCELLED";

    /** RUNNING 超时后的回收目标状态：失败（绝不标记 SUCCESS） */
    private static final String RECOVER_RUNNING_TARGET = "FAILED";

    /** 合法实例状态 */
    private static final Set<String> INSTANCE_STATUSES = Set.of(
            "PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED");

    /** 终态：不允许再流转到其他状态 */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCESS", "FAILED", "CANCELLED");

    /** 手动触发固定触发方式 */
    private static final String TRIGGER_TYPE_MANUAL = "MANUAL";

    /** 未实现执行器时的固定提示 */
    private static final String EXECUTOR_NOT_IMPLEMENTED = "executor not implemented";

    /** 允许重试的原实例终态 */
    private static final Set<String> RETRYABLE_STATUSES = Set.of(
            "SUCCESS", "FAILED", "CANCELLED");

    /** 允许人工取消的活跃状态 */
    private static final Set<String> CANCELLABLE_STATUSES = Set.of("PENDING", "RUNNING");

    /** 取消原因为空时的默认值 */
    private static final String DEFAULT_CANCEL_REASON = "人工取消";

    /** 取消原因最大长度（与 DTO 校验保持一致） */
    private static final int MAX_CANCEL_REASON_LENGTH = 2000;

    /** 任务定义 Mapper（校验来源定义并加行锁） */
    private final TaskDefinitionMapper taskDefinitionMapper;

    /** 任务实例 Mapper */
    private final TaskInstanceMapper taskInstanceMapper;

    /** 平台事务管理器：超时回收整批一个事务，单条用 SAVEPOINT 隔离 */
    private final PlatformTransactionManager transactionManager;

    /** PENDING 实例超时阈值（分钟），默认 60 */
    @Value("${dam.task-center.pending-timeout-minutes:60}")
    private long pendingTimeoutMinutes;

    /** RUNNING 实例超时阈值（分钟），默认 180 */
    @Value("${dam.task-center.running-timeout-minutes:180}")
    private long runningTimeoutMinutes;

    /** 回收扫描间隔（毫秒），默认 60000；仅用于状态接口回显 */
    @Value("${dam.task-center.recover-interval-ms:60000}")
    private long recoverIntervalMs;

    /** 最近一次成功扫描的时间，供状态接口查询；volatile 保证调度线程写入可见 */
    private volatile LocalDateTime lastScanAt;

    /** 最近一次成功扫描回收的实例总数 */
    private volatile int lastRecoveredCount;

    /**
     * 手动触发一次任务。
     * <p>
     * 在任务定义行锁内分配 run_no，写入一条 status=PENDING、
     * trigger_type=MANUAL 的实例；不执行任务，也不伪造执行成功。
     *
     * @param definitionId 任务定义ID
     * @return accepted/instanceId/runNo/status/message 等触发受理信息
     */
    @Transactional
    public Map<String, Object> trigger(Long definitionId) {
        if (definitionId == null) {
            throw new BusinessException(400, "任务定义ID不能为空");
        }
        TaskDefinition definition = taskDefinitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new BusinessException(404, "任务定义不存在：" + definitionId);
        }
        // 对定义行加锁，串行分配同一任务内的 run_no；已删除时返回 null
        if (taskDefinitionMapper.lockById(definitionId) == null) {
            throw new BusinessException(404, "任务定义不存在：" + definitionId);
        }
        Integer maxRunNo = taskInstanceMapper.selectMaxRunNo(definitionId);
        int runNo = maxRunNo == null ? 1 : maxRunNo + 1;

        TaskInstance instance = new TaskInstance();
        instance.setDefinitionId(definition.getId());
        instance.setTaskCode(definition.getTaskCode());
        instance.setRunNo(runNo);
        instance.setStatus("PENDING");
        instance.setTriggerType(TRIGGER_TYPE_MANUAL);
        instance.setCreatedAt(LocalDateTime.now());
        taskInstanceMapper.insert(instance);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("instanceId", instance.getId());
        result.put("definitionId", definition.getId());
        result.put("taskCode", definition.getTaskCode());
        result.put("runNo", runNo);
        result.put("status", "PENDING");
        result.put("triggerType", TRIGGER_TYPE_MANUAL);
        result.put("message", EXECUTOR_NOT_IMPLEMENTED);
        return result;
    }

    /**
     * 基于历史终态实例重试，创建一条新的 PENDING 实例。
     * <p>
     * 仅允许原实例处于 SUCCESS/FAILED/CANCELLED 终态；PENDING/RUNNING 说明任务仍活跃，
     * 返回 409。新实例沿用原实例的 definitionId 与最新 taskCode，status=PENDING、
     * triggerType=MANUAL，run_no 在任务定义行锁（{@code lockById}）内分配，多实例
     * 并发重试/触发时不会重复。原实例不做任何修改；不执行任务，消息不伪造执行成功。
     *
     * @param instanceId 原任务实例ID
     * @return newInstanceId/definitionId/taskCode/runNo/status/triggerType/message 等受理信息
     */
    @Transactional
    public Map<String, Object> retry(Long instanceId) {
        TaskInstance original = requireInstance(instanceId);
        String current = normalizeExistingStatus(original.getStatus());
        if ("PENDING".equals(current) || "RUNNING".equals(current)) {
            throw new BusinessException(409,
                    "实例仍在等待执行或正在执行，不允许重试：" + instanceId);
        }
        if (current == null || !RETRYABLE_STATUSES.contains(current)) {
            throw new BusinessException(409,
                    "仅允许重试终态实例（SUCCESS/FAILED/CANCELLED），当前状态："
                            + original.getStatus());
        }

        Long definitionId = original.getDefinitionId();
        if (definitionId == null) {
            throw new BusinessException(404, "任务定义不存在：原实例未关联任务定义");
        }
        // 对定义行加锁，串行分配同一任务内的 run_no；已删除或不存在时返回 null
        if (taskDefinitionMapper.lockById(definitionId) == null) {
            throw new BusinessException(404, "任务定义不存在：" + definitionId);
        }
        TaskDefinition definition = taskDefinitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new BusinessException(404, "任务定义不存在：" + definitionId);
        }

        Integer maxRunNo = taskInstanceMapper.selectMaxRunNo(definitionId);
        int runNo = maxRunNo == null ? 1 : maxRunNo + 1;

        TaskInstance instance = new TaskInstance();
        instance.setDefinitionId(definition.getId());
        instance.setTaskCode(definition.getTaskCode());
        instance.setRunNo(runNo);
        instance.setStatus("PENDING");
        instance.setTriggerType(TRIGGER_TYPE_MANUAL);
        instance.setCreatedAt(LocalDateTime.now());
        taskInstanceMapper.insert(instance);

        log.info("任务实例重试受理: previousInstanceId={}, newInstanceId={}, definitionId={}, runNo={}",
                instanceId, instance.getId(), definition.getId(), runNo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newInstanceId", instance.getId());
        result.put("instanceId", instance.getId());
        result.put("retriedFromInstanceId", instanceId);
        result.put("definitionId", definition.getId());
        result.put("taskCode", definition.getTaskCode());
        result.put("runNo", runNo);
        result.put("status", "PENDING");
        result.put("triggerType", TRIGGER_TYPE_MANUAL);
        result.put("message", "retry instance created, " + EXECUTOR_NOT_IMPLEMENTED);
        return result;
    }

    /**
     * 人工取消 PENDING/RUNNING 实例。
     * <p>
     * 已 CANCELLED 的实例视为幂等成功，直接返回现有记录（保留原 finishedAt/errorMessage）；
     * SUCCESS/FAILED 终态返回 409。取消在 {@code SELECT ... FOR UPDATE} 行锁内完成，
     * 与回调结束/状态回填串行化：成功取消时 status=CANCELLED、finishedAt=now、
     * errorMessage=请求 reason（空白则默认“人工取消”）。
     *
     * @param instanceId 任务实例ID
     * @param request    取消请求，可为 null
     * @return 取消后的实例（或已取消的现有实例）
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskInstance cancel(Long instanceId, TaskInstanceCancelRequest request) {
        if (instanceId == null) {
            throw new BusinessException(400, "任务实例ID不能为空");
        }
        String reason = resolveCancelReason(request);
        TaskInstance instance = taskInstanceMapper.selectByIdForUpdate(instanceId);
        if (instance == null) {
            throw new BusinessException(404, "任务实例不存在：" + instanceId);
        }

        String current = normalizeExistingStatus(instance.getStatus());
        if ("CANCELLED".equals(current)) {
            return instance;
        }
        if (current == null || !CANCELLABLE_STATUSES.contains(current)) {
            throw new BusinessException(409,
                    "仅允许取消 PENDING/RUNNING 实例，当前状态：" + instance.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        instance.setStatus("CANCELLED");
        instance.setFinishedAt(now);
        instance.setErrorMessage(reason);
        if (taskInstanceMapper.updateById(instance) == 0) {
            throw new BusinessException(404, "任务实例不存在：" + instanceId);
        }
        log.info("任务实例人工取消: instanceId={}, previousStatus={}", instanceId, current);
        return instance;
    }

    /**
     * 解析取消原因：空白时回退默认值，超长时返回 400。
     *
     * @param request 取消请求（可空）
     * @return 非空取消原因
     */
    private static String resolveCancelReason(TaskInstanceCancelRequest request) {
        if (request == null || !hasText(request.getReason())) {
            return DEFAULT_CANCEL_REASON;
        }
        String reason = request.getReason().trim();
        if (reason.length() > MAX_CANCEL_REASON_LENGTH) {
            throw new BusinessException(400,
                    "reason 长度不能超过 " + MAX_CANCEL_REASON_LENGTH);
        }
        return reason;
    }

    /**
     * 归一化数据库中已有的状态值，不抛异常（用于重试/取消的前置判断）。
     *
     * @param raw 数据库状态
     * @return 大写状态；为空时返回 null
     */
    private static String normalizeExistingStatus(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 分页查询任务实例。
     *
     * @param definitionId 任务定义ID过滤（可空）
     * @param status       实例状态过滤（可空）
     * @param page         页码（从 1 开始）
     * @param pageSize     每页条数（1~100）
     * @return 分页结果
     */
    public PageResult<TaskInstance> page(Long definitionId, String status,
                                         Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null ? 20 : Math.clamp(pageSize, 1, MAX_PAGE_SIZE);

        LambdaQueryWrapper<TaskInstance> wrapper = new LambdaQueryWrapper<>();
        if (definitionId != null) {
            wrapper.eq(TaskInstance::getDefinitionId, definitionId);
        }
        if (hasText(status)) {
            wrapper.eq(TaskInstance::getStatus, normalizeStatus(status));
        }
        wrapper.orderByDesc(TaskInstance::getCreatedAt)
                .orderByDesc(TaskInstance::getId);
        return PageResult.of(taskInstanceMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /**
     * 查询任务实例详情。
     *
     * @param id 实例ID
     * @return 任务实例
     */
    public TaskInstance getById(Long id) {
        return requireInstance(id);
    }

    /**
     * 更新任务实例状态。
     * <p>
     * 仅允许状态机定义内的流转；非法目标状态或非法流转均返回 400。
     * 进入 RUNNING 时回填 started_at；进入终态时回填 finished_at，
     * 直达 SUCCESS/FAILED 时同时回填 started_at。
     *
     * @param id      实例ID
     * @param request 状态请求（status，可选 errorMessage）
     * @return 更新后的实例
     */
    @Transactional
    public TaskInstance updateStatus(Long id, TaskStatusRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        TaskInstance instance = requireInstance(id);
        String target = normalizeStatus(request.getStatus());
        String current = instance.getStatus();
        if (!canTransition(current, target)) {
            throw new BusinessException(400,
                    "非法状态流转：" + current + " -> " + target);
        }
        boolean failed = "FAILED".equals(target);
        if (hasText(request.getErrorMessage()) && !failed) {
            throw new BusinessException(400, "仅 FAILED 状态允许设置 errorMessage");
        }
        if (request.getErrorMessage() != null && request.getErrorMessage().trim().length() > 2000) {
            throw new BusinessException(400, "errorMessage 长度不能超过 2000");
        }

        if (!current.equals(target)) {
            LocalDateTime now = LocalDateTime.now();
            instance.setStatus(target);
            if ("RUNNING".equals(target)) {
                if (instance.getStartedAt() == null) {
                    instance.setStartedAt(now);
                }
            } else if (TERMINAL_STATUSES.contains(target)) {
                if (instance.getStartedAt() == null && !"CANCELLED".equals(target)) {
                    // 允许从 PENDING 直接完成的执行器直达终态
                    instance.setStartedAt(now);
                }
                instance.setFinishedAt(now);
            }
        }
        if (hasText(request.getErrorMessage())) {
            instance.setErrorMessage(request.getErrorMessage().trim());
        }
        if (taskInstanceMapper.updateById(instance) == 0) {
            throw new BusinessException(404, "任务实例不存在：" + id);
        }
        return instance;
    }

    /**
     * 查询超时回收配置与最近一次扫描结果。
     * <p>
     * 供运维确认当前阈值、扫描间隔以及最近一次扫描时间/回收条数；未执行过扫描时
     * lastScanAt 为 null、lastRecoveredCount 为 0。
     *
     * @return 配置与最近扫描结果
     */
    public Map<String, Object> recoveryStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("pendingTimeoutMinutes", pendingTimeoutMinutes);
        status.put("runningTimeoutMinutes", runningTimeoutMinutes);
        status.put("recoverIntervalMs", recoverIntervalMs);
        status.put("initialDelayMs", RECOVER_INITIAL_DELAY_MS);
        status.put("batchSize", RECOVER_BATCH_SIZE);
        status.put("pendingRecoveredAs", RECOVER_PENDING_TARGET);
        status.put("runningRecoveredAs", RECOVER_RUNNING_TARGET);
        status.put("lastScanAt", lastScanAt);
        status.put("lastRecoveredCount", lastRecoveredCount);
        return status;
    }

    /**
     * 定时扫描并回收超时任务实例。
     * <p>
     * 启动后延迟 {@value #RECOVER_INITIAL_DELAY_MS} 毫秒首次执行，之后按
     * {@code dam.task-center.recover-interval-ms}（默认 60000）固定延迟重复执行。
     * 调度线程内的失败只记录日志，不向上抛出，避免调度器因单次数据库异常停止后续扫描。
     */
    @Scheduled(fixedDelayString = "${dam.task-center.recover-interval-ms:60000}",
            initialDelayString = "60000")
    public void scheduledRecoverTimeoutInstances() {
        try {
            Map<String, Object> result = recoverTimeoutInstances();
            int recovered = (Integer) result.get("recoveredCount");
            if (recovered > 0) {
                log.warn("任务实例超时回收完成: recovered={}, pending={}, running={}",
                        recovered, result.get("pendingRecoveredCount"),
                        result.get("runningRecoveredCount"));
            } else {
                log.debug("任务实例超时回收扫描完成: 无超时实例");
            }
        } catch (Exception e) {
            log.error("任务实例超时回收扫描失败", e);
        }
    }

    /**
     * 立即执行一次超时回收扫描（定时任务与手动接口共用）。
     * <p>
     * 整批扫描与条件更新在一个事务内完成；候选行使用 {@code FOR UPDATE SKIP LOCKED}
     * 加锁，多实例并发扫描时同一条实例只会被一个节点处理。PENDING 超过
     * {@code pending-timeout-minutes} 标记 CANCELLED，RUNNING 超过
     * {@code running-timeout-minutes} 标记 FAILED，finished_at 统一回填为扫描时间，
     * error_message 固定为“实例超时自动回收”；不会把任何实例标记为 SUCCESS。
     * 单次最多处理 100 条，单条异常通过 SAVEPOINT 回滚后继续处理其余记录。
     *
     * @return 本次扫描时间、回收总数与分类计数
     */
    public Map<String, Object> recoverTimeoutInstances() {
        LocalDateTime scanAt = LocalDateTime.now();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        RecoveryResult result = template.execute(status -> recoverInTransaction(scanAt));
        if (result == null) {
            throw new IllegalStateException("任务实例超时回收未返回结果");
        }
        // 仅在事务成功提交后更新对外可见的扫描状态，避免回滚后状态失真
        this.lastScanAt = scanAt;
        this.lastRecoveredCount = result.total();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scanAt", scanAt);
        summary.put("recoveredCount", result.total());
        summary.put("pendingRecoveredCount", result.pendingRecovered());
        summary.put("runningRecoveredCount", result.runningRecovered());
        summary.put("pendingTimeoutMinutes", pendingTimeoutMinutes);
        summary.put("runningTimeoutMinutes", runningTimeoutMinutes);
        return summary;
    }

    /**
     * 在调用方事务内完成候选扫描与批量回收。
     * <p>
     * 先扫描 PENDING 超时候选，再用剩余配额扫描 RUNNING 候选，保证单次合计不超过
     * {@value #RECOVER_BATCH_SIZE} 条。每条记录在嵌套事务（JDBC SAVEPOINT）内执行
     * 带状态条件的 UPDATE：状态已被其他节点/执行器改变时更新 0 行并跳过；
     * 单条 SQL 异常只回滚该条 SAVEPOINT，不中断整批。
     *
     * @param scanAt 本次扫描基准时间，用于计算超时截止点并作为 finished_at
     * @return 分类回收计数
     */
    private RecoveryResult recoverInTransaction(LocalDateTime scanAt) {
        LocalDateTime pendingCutoff = scanAt.minusMinutes(Math.max(1L, pendingTimeoutMinutes));
        LocalDateTime runningCutoff = scanAt.minusMinutes(Math.max(1L, runningTimeoutMinutes));

        List<Long> pendingIds = taskInstanceMapper.selectTimeoutPendingIds(
                pendingCutoff, RECOVER_BATCH_SIZE);
        int remaining = RECOVER_BATCH_SIZE - pendingIds.size();
        List<Long> runningIds = remaining > 0
                ? taskInstanceMapper.selectTimeoutRunningIds(runningCutoff, remaining)
                : List.of();

        TransactionTemplate nested = new TransactionTemplate(transactionManager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);

        int pendingRecovered = recoverBatch(nested, pendingIds,
                "PENDING", RECOVER_PENDING_TARGET, scanAt);
        int runningRecovered = recoverBatch(nested, runningIds,
                "RUNNING", RECOVER_RUNNING_TARGET, scanAt);
        return new RecoveryResult(pendingRecovered, runningRecovered);
    }

    /**
     * 逐条回收一批候选实例，单条失败只跳过该条。
     *
     * @param nestedTemplate 嵌套事务模板（SAVEPOINT），隔离单条失败
     * @param ids            候选实例ID
     * @param expectedStatus 期望的当前状态
     * @param targetStatus   回收目标状态（CANCELLED/FAILED）
     * @param finishedAt     结束时间
     * @return 实际回收条数
     */
    private int recoverBatch(TransactionTemplate nestedTemplate,
                             List<Long> ids,
                             String expectedStatus,
                             String targetStatus,
                             LocalDateTime finishedAt) {
        int recovered = 0;
        for (Long id : ids) {
            try {
                Integer updated = nestedTemplate.execute(status ->
                        taskInstanceMapper.recoverTimeoutById(id, expectedStatus,
                                targetStatus, finishedAt, RECOVER_ERROR_MESSAGE));
                if (updated != null && updated > 0) {
                    recovered++;
                    log.debug("回收超时任务实例: id={}, {} -> {}", id, expectedStatus, targetStatus);
                }
            } catch (Exception e) {
                // 单条异常仅回滚该条 SAVEPOINT，继续处理其余候选
                log.warn("回收超时任务实例失败，已跳过: id={}, status={}",
                        id, expectedStatus, e);
            }
        }
        return recovered;
    }

    /**
     * 单次回收扫描结果的分类计数。
     *
     * @param pendingRecovered PENDING → CANCELLED 条数
     * @param runningRecovered RUNNING → FAILED 条数
     */
    private record RecoveryResult(int pendingRecovered, int runningRecovered) {

        /**
         * 回收总数。
         *
         * @return pendingRecovered + runningRecovered
         */
        int total() {
            return pendingRecovered + runningRecovered;
        }
    }

    /**
     * 按 ID 查询任务实例，不存在时抛出 404 业务异常。
     *
     * @param id 实例ID
     * @return 任务实例
     */
    private TaskInstance requireInstance(Long id) {
        if (id == null) {
            throw new BusinessException(400, "任务实例ID不能为空");
        }
        TaskInstance instance = taskInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BusinessException(404, "任务实例不存在：" + id);
        }
        return instance;
    }

    /**
     * 判断实例状态流转是否合法。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 是否允许
     */
    private static boolean canTransition(String from, String to) {
        if (from == null) {
            return false;
        }
        if (from.equals(to)) {
            return true;
        }
        return switch (from) {
            case "PENDING" -> Set.of("RUNNING", "SUCCESS", "FAILED", "CANCELLED").contains(to);
            case "RUNNING" -> Set.of("SUCCESS", "FAILED", "CANCELLED").contains(to);
            // SUCCESS/FAILED/CANCELLED 为终态，不允许再流转
            default -> false;
        };
    }

    /**
     * 归一化并校验实例状态。
     *
     * @param raw 原始状态
     * @return 大写状态
     */
    private static String normalizeStatus(String raw) {
        if (!hasText(raw)) {
            throw new BusinessException(400, "status 不能为空");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!INSTANCE_STATUSES.contains(value)) {
            throw new BusinessException(400,
                    "status 不合法，仅支持 PENDING/RUNNING/SUCCESS/FAILED/CANCELLED：" + raw);
        }
        return value;
    }

    /**
     * 判断文本是否包含非空白字符。
     *
     * @param value 原始文本
     * @return 是否非空
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
