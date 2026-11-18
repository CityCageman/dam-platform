package com.dam.scheduler.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.scheduler.config.DolphinSchedulerProperties;
import com.dam.scheduler.entity.ScheduleJob;
import com.dam.scheduler.entity.ScheduleRun;
import com.dam.scheduler.repository.ScheduleJobMapper;
import com.dam.scheduler.repository.ScheduleRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 调度任务管理服务。
 * <p>
 * 负责调度任务（schedule_job）的 CRUD、启停、一次性触发与运行记录查询。
 * 触发动作落一条 PENDING 运行记录，并在事务提交后交给
 * {@link ScheduleRunExecutor} 异步执行关联的顺序 SQL DAG；运行开始/结束的
 * TaskCenter 回调也统一由执行器负责，本服务不重复登记。
 * <p>
 * 每个任务通过 {@code execution_engine} 选择执行引擎：LOCAL 保持原有本地 DAG 执行，
 * DOLPHIN 触发 DolphinScheduler 流程实例；新建任务未指定时取全局默认引擎，
 * 默认 LOCAL，保证存量接口与行为兼容。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class SchedulerService {

    /** 单页最大条数，避免 pageSize 过大拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 任务编码最大长度（与 schedule_job.job_code 一致） */
    private static final int MAX_CODE_LENGTH = 64;

    /** 任务名称最大长度（与 schedule_job.job_name 一致） */
    private static final int MAX_NAME_LENGTH = 200;

    /** Cron 表达式最大长度（与 schedule_job.cron_expression 一致） */
    private static final int MAX_CRON_LENGTH = 100;

    /** DAG 编码最大长度（与 schedule_job.dag_code 一致） */
    private static final int MAX_DAG_CODE_LENGTH = 64;

    /** 描述最大长度 */
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    /** 关键字最大长度 */
    private static final int MAX_KEYWORD_LENGTH = 100;

    /** 任务全量状态 */
    private static final Set<String> JOB_STATUSES = Set.of("DRAFT", "ENABLED", "DISABLED");

    /** 启停接口允许的状态 */
    private static final Set<String> TOGGLE_STATUSES = Set.of("ENABLED", "DISABLED");

    /** 任务允许的执行引擎 */
    private static final Set<String> EXECUTION_ENGINES = Set.of(
            DolphinSchedulerProperties.ENGINE_LOCAL,
            DolphinSchedulerProperties.ENGINE_DOLPHIN);

    private final ScheduleJobMapper jobMapper;
    private final ScheduleRunMapper runMapper;
    private final ScheduleRunExecutor runExecutor;
    private final DolphinSchedulerProperties schedulerProperties;

    /**
     * 分页查询调度任务。
     *
     * @param keyword  任务编码/名称关键字，可为空
     * @param status   状态过滤：DRAFT/ENABLED/DISABLED，可为空
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数，最大 100
     * @return 分页结果
     */
    public PageResult<ScheduleJob> pageJobs(String keyword, String status, Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null ? 20 : Math.clamp(pageSize, 1, MAX_PAGE_SIZE);

        LambdaQueryWrapper<ScheduleJob> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            if (kw.length() > MAX_KEYWORD_LENGTH) {
                kw = kw.substring(0, MAX_KEYWORD_LENGTH);
            }
            String finalKw = kw;
            wrapper.and(w -> w.like(ScheduleJob::getJobCode, finalKw)
                    .or().like(ScheduleJob::getJobName, finalKw));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ScheduleJob::getStatus,
                    normalizeStatus(status, JOB_STATUSES, "不支持的任务状态"));
        }
        wrapper.orderByDesc(ScheduleJob::getCreatedAt).orderByDesc(ScheduleJob::getId);
        return PageResult.of(jobMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务ID
     * @return 任务详情
     */
    public ScheduleJob getJob(Long id) {
        ScheduleJob job = id == null ? null : jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(404, "调度任务不存在: " + id);
        }
        return job;
    }

    /**
     * 创建调度任务。
     * <p>
     * 校验 jobCode/jobName/cronExpression，并保证 jobCode 全局唯一；
     * 未指定状态时默认为 DRAFT。
     *
     * @param request 任务定义
     * @return 创建后的任务
     */
    @Transactional
    public ScheduleJob createJob(ScheduleJob request) {
        if (request == null) {
            throw new BusinessException(400, "任务定义不能为空");
        }
        String jobCode = requireText(request.getJobCode(), "任务编码", MAX_CODE_LENGTH);
        String jobName = requireText(request.getJobName(), "任务名称", MAX_NAME_LENGTH);
        String cron = validateCron(request.getCronExpression());
        String dagCode = optionalText(request.getDagCode(), "DAG 编码", MAX_DAG_CODE_LENGTH);
        String description = optionalText(request.getDescription(), "任务描述", MAX_DESCRIPTION_LENGTH);
        String status = StringUtils.hasText(request.getStatus())
                ? normalizeStatus(request.getStatus(), JOB_STATUSES, "不支持的任务状态")
                : "DRAFT";
        String executionEngine = StringUtils.hasText(request.getExecutionEngine())
                ? normalizeEngine(request.getExecutionEngine())
                : (schedulerProperties.isDolphinEngine()
                        ? DolphinSchedulerProperties.ENGINE_DOLPHIN
                        : DolphinSchedulerProperties.ENGINE_LOCAL);
        Long dolphinProcessDefinitionCode = request.getDolphinProcessDefinitionCode();
        Integer dolphinProcessDefinitionVersion = request.getDolphinProcessDefinitionVersion();
        validateEngineBinding(executionEngine, dolphinProcessDefinitionCode,
                dolphinProcessDefinitionVersion);

        if (jobMapper.countByCodeIncludingDeleted(jobCode) > 0) {
            throw new BusinessException(409, "任务编码已存在: " + jobCode);
        }

        String operator = currentUser();
        LocalDateTime now = LocalDateTime.now();
        ScheduleJob job = new ScheduleJob();
        job.setJobCode(jobCode);
        job.setJobName(jobName);
        job.setCronExpression(cron);
        job.setDagCode(dagCode);
        job.setExecutionEngine(executionEngine);
        job.setDolphinProcessDefinitionCode(dolphinProcessDefinitionCode);
        job.setDolphinProcessDefinitionVersion(dolphinProcessDefinitionVersion);
        job.setStatus(status);
        job.setDescription(description);
        job.setCreatedAt(now);
        job.setCreatedBy(operator);
        job.setUpdatedAt(now);
        job.setUpdatedBy(operator);
        try {
            jobMapper.insert(job);
        } catch (DuplicateKeyException e) {
            // 并发创建时唯一索引兜底，避免把数据库异常暴露为 500
            throw new BusinessException(409, "任务编码已存在: " + jobCode);
        }
        return job;
    }

    /**
     * 更新调度任务。
     * <p>
     * jobCode 创建后不允许修改；jobName/cronExpression 传入时重新校验。
     *
     * @param id      任务ID
     * @param request 待更新字段
     * @return 更新后的任务
     */
    @Transactional
    public ScheduleJob updateJob(Long id, ScheduleJob request) {
        ScheduleJob job = getJob(id);
        if (request == null) {
            throw new BusinessException(400, "任务定义不能为空");
        }
        if (request.getJobCode() != null
                && !request.getJobCode().trim().equals(job.getJobCode())) {
            throw new BusinessException(400, "任务编码不允许修改");
        }
        if (request.getJobName() != null) {
            job.setJobName(requireText(request.getJobName(), "任务名称", MAX_NAME_LENGTH));
        }
        if (request.getCronExpression() != null) {
            job.setCronExpression(validateCron(request.getCronExpression()));
        }
        if (request.getDagCode() != null) {
            job.setDagCode(optionalText(request.getDagCode(), "DAG 编码", MAX_DAG_CODE_LENGTH));
        }
        if (request.getDescription() != null) {
            job.setDescription(optionalText(request.getDescription(), "任务描述", MAX_DESCRIPTION_LENGTH));
        }
        if (request.getStatus() != null) {
            job.setStatus(normalizeStatus(request.getStatus(), JOB_STATUSES, "不支持的任务状态"));
        }
        if (request.getExecutionEngine() != null) {
            job.setExecutionEngine(normalizeEngine(request.getExecutionEngine()));
        }
        if (request.getDolphinProcessDefinitionCode() != null) {
            job.setDolphinProcessDefinitionCode(request.getDolphinProcessDefinitionCode());
        }
        if (request.getDolphinProcessDefinitionVersion() != null) {
            job.setDolphinProcessDefinitionVersion(request.getDolphinProcessDefinitionVersion());
        }
        validateEngineBinding(job.getExecutionEngine(),
                job.getDolphinProcessDefinitionCode(),
                job.getDolphinProcessDefinitionVersion());
        job.setUpdatedAt(LocalDateTime.now());
        job.setUpdatedBy(currentUser());
        jobMapper.updateById(job);
        return job;
    }

    /**
     * 逻辑删除调度任务。
     *
     * @param id 任务ID
     */
    @Transactional
    public void deleteJob(Long id) {
        getJob(id);
        // @TableLogic 自动将 deleted_at 置为 now()，历史运行记录保留
        jobMapper.deleteById(id);
    }

    /**
     * 启用/停用调度任务。
     *
     * @param id     任务ID
     * @param status ENABLED/DISABLED
     * @return 更新后的任务
     */
    @Transactional
    public ScheduleJob updateJobStatus(Long id, String status) {
        ScheduleJob job = getJob(id);
        job.setStatus(normalizeStatus(status, TOGGLE_STATUSES, "启停状态仅支持 ENABLED/DISABLED"));
        job.setUpdatedAt(LocalDateTime.now());
        job.setUpdatedBy(currentUser());
        jobMapper.updateById(job);
        return job;
    }

    /**
     * 手动触发一次调度任务。
     * <p>
     * 只写入 PENDING 运行记录，事务提交后异步提交执行器；不伪造 SUCCESS。
     * 返回 accepted=true 表示触发请求已受理。
     * 手动触发不修改 {@code last_dispatched_at}，不干扰 Cron 自动派发节奏。
     * <p>
     * TaskCenter 的开始/结束回调由 {@link ScheduleRunExecutor} 在执行时统一登记，
     * 本方法不再提前登记，避免同一 run 产生重复实例。
     *
     * @param id 任务ID
     * @return accepted/runId/message
     */
    @Transactional
    public Map<String, Object> triggerJob(Long id) {
        ScheduleJob job = getJob(id);
        ScheduleRun run = new ScheduleRun();
        run.setJobId(job.getId());
        run.setRunStatus("PENDING");
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);

        // 必须等本次 trigger 事务提交后再提交执行，避免工作线程读不到 PENDING 运行记录。
        // 若事务回滚，afterCommit 不会触发，不会产生无运行记录的孤儿执行。
        runExecutor.submitAfterCommit(run.getId(), job.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("runId", run.getId());
        result.put("message", "execution submitted");
        return result;
    }

    /**
     * 查询任务运行记录，按创建时间/ID 倒序。
     *
     * @param jobId 任务ID
     * @return 运行记录列表
     */
    public List<ScheduleRun> listRuns(Long jobId) {
        // 先校验任务存在，避免对已删除/不存在的任务返回历史记录
        getJob(jobId);
        return runMapper.selectList(new LambdaQueryWrapper<ScheduleRun>()
                .eq(ScheduleRun::getJobId, jobId)
                .orderByDesc(ScheduleRun::getCreatedAt)
                .orderByDesc(ScheduleRun::getId));
    }

    /**
     * 查询调度服务状态。
     * <p>
     * executionEngine=sequential-sql-dag：手动触发与 Cron 派发的 PENDING 运行记录
     * 会由执行器异步执行关联 DAG 的 SQL 节点，并回填 SUCCESS/FAILED。
     *
     * @return 服务名、状态、角色、存储模式与执行器模式
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "dam-scheduler");
        status.put("status", "UP");
        status.put("role", "data scheduler");
        status.put("storage", "dam_scheduler");
        status.put("executionEngine", "sequential-sql-dag");
        status.put("executionEngineNote",
                "支持基础顺序 SQL DAG 执行；不支持并行分支、条件分支与重试编排");
        return status;
    }

    /**
     * 校验必填文本并裁剪。
     *
     * @param raw     原始值
     * @param field   字段名
     * @param maxLen  最大长度
     * @return 裁剪后的文本
     */
    private static String requireText(String raw, String field, int maxLen) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(400, field + "不能为空");
        }
        String value = raw.trim();
        if (value.length() > maxLen) {
            throw new BusinessException(400, field + "不能超过 " + maxLen + " 个字符");
        }
        return value;
    }

    /**
     * 校验可选文本并裁剪，空白值归一为 null。
     *
     * @param raw    原始值
     * @param field  字段名
     * @param maxLen 最大长度
     * @return 裁剪后的文本或 null
     */
    private static String optionalText(String raw, String field, int maxLen) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > maxLen) {
            throw new BusinessException(400, field + "不能超过 " + maxLen + " 个字符");
        }
        return value;
    }

    /**
     * 校验 Spring 六段式 Cron 表达式。
     *
     * @param raw 原始表达式
     * @return 裁剪后的表达式
     */
    private static String validateCron(String raw) {
        String cron = requireText(raw, "Cron 表达式", MAX_CRON_LENGTH);
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Cron 表达式不合法: " + e.getMessage());
        }
        return cron;
    }

    /**
     * 归一化并校验状态值。
     *
     * @param raw     原始状态
     * @param allowed 允许的状态集合
     * @param message 错误提示前缀
     * @return 大写状态值
     */
    private static String normalizeStatus(String raw, Set<String> allowed, String message) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(400, "任务状态不能为空");
        }
        String status = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(status)) {
            throw new BusinessException(400, message + ": " + raw);
        }
        return status;
    }

    /**
     * 归一化并校验执行引擎，仅支持 LOCAL/DOLPHIN。
     *
     * @param raw 原始引擎值
     * @return 大写引擎值
     */
    private static String normalizeEngine(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(400, "执行引擎不能为空");
        }
        String engine = raw.trim().toUpperCase(Locale.ROOT);
        if (!EXECUTION_ENGINES.contains(engine)) {
            throw new BusinessException(400, "不支持的执行引擎: " + raw);
        }
        return engine;
    }

    /**
     * 校验执行引擎与 DolphinScheduler 流程定义绑定是否匹配。
     * <p>
     * DOLPHIN 引擎必须有流程定义编码，避免触发时才失败；LOCAL 引擎忽略 DS 字段，
     * 允许保留历史绑定便于切换。版本号为空表示由 DS 侧决定。
     *
     * @param engine  执行引擎
     * @param code    DS 流程定义编码，可为空
     * @param version DS 流程定义版本，可为空
     */
    private static void validateEngineBinding(String engine, Long code, Integer version) {
        if (code != null && code <= 0) {
            throw new BusinessException(400, "dolphinProcessDefinitionCode 必须为正整数");
        }
        if (version != null && version <= 0) {
            throw new BusinessException(400, "dolphinProcessDefinitionVersion 必须为正整数");
        }
        if (DolphinSchedulerProperties.ENGINE_DOLPHIN.equals(engine) && code == null) {
            throw new BusinessException(400,
                    "DOLPHIN 执行引擎需配置 dolphinProcessDefinitionCode");
        }
    }

    /**
     * 当前操作人；未登录或解析失败时使用 system。
     *
     * @return 操作人标识
     */
    private static String currentUser() {
        try {
            Object id = StpUtil.getLoginIdDefaultNull();
            return id == null ? "system" : String.valueOf(id);
        } catch (Exception e) {
            return "system";
        }
    }
}
