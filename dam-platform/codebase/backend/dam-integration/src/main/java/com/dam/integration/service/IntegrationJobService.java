package com.dam.integration.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.security.CredentialCipher;
import com.dam.common.vo.PageResult;
import com.dam.integration.entity.IntegrationJob;
import com.dam.integration.entity.IntegrationRun;
import com.dam.integration.repository.IntegrationJobMapper;
import com.dam.integration.repository.IntegrationRunMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据集成任务管理服务
 * <p>
 * 负责 integration_job 的分页查询、详情、创建、更新、逻辑删除与状态切换。
 * trigger 接口在事务内创建 PENDING 运行记录，提交后把真实 JDBC 全量同步提交到线程池，
 * 不阻塞 HTTP 请求；同一任务已有 PENDING/RUNNING 记录时返回 409。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationJobService {

    /** 允许的任务状态 */
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            IntegrationJob.STATUS_DRAFT,
            IntegrationJob.STATUS_ENABLED,
            IntegrationJob.STATUS_DISABLED,
            IntegrationJob.STATUS_RUNNING,
            IntegrationJob.STATUS_FAILED);

    /** 状态切换接口允许的目标状态 */
    private static final Set<String> SWITCHABLE_STATUSES = Set.of(
            IntegrationJob.STATUS_ENABLED,
            IntegrationJob.STATUS_DISABLED);

    /** 默认页码 */
    private static final int DEFAULT_PAGE = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页最大条数，避免一次拉取过多数据 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 集成任务 Mapper */
    private final IntegrationJobMapper jobMapper;

    /** 运行记录 Mapper */
    private final IntegrationRunMapper runMapper;

    /** 真实 JDBC 同步执行服务 */
    private final IntegrationExecutionService executionService;

    /** 配置凭据加解密组件（sourceConfig/targetConfig 的 password 落库前加密） */
    private final CredentialCipher credentialCipher;

    /** 同步执行线程池（由 IntegrationAsyncConfig 提供） */
    @Resource(name = "integrationTaskExecutor")
    private ThreadPoolTaskExecutor integrationTaskExecutor;

    /**
     * 分页查询集成任务
     *
     * @param keyword  关键字，模糊匹配任务编码或任务名称
     * @param status   任务状态精确过滤，可为空
     * @param page     页码，从 1 开始，非法值按 1 处理
     * @param pageSize 每页条数，非法值按 20 处理，最大 200
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<IntegrationJob> pageJobs(String keyword, String status, Integer page, Integer pageSize) {
        long current = page == null || page < 1 ? DEFAULT_PAGE : page;
        long size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<IntegrationJob> wrapper = new LambdaQueryWrapper<>();
        String keywordValue = trimToNull(keyword);
        if (keywordValue != null) {
            // 关键字条件用 and 包裹，避免 or 扩散到状态过滤条件
            wrapper.and(w -> w.like(IntegrationJob::getJobCode, keywordValue)
                    .or().like(IntegrationJob::getJobName, keywordValue));
        }
        String statusValue = normalizeStatus(status);
        if (statusValue != null) {
            wrapper.eq(IntegrationJob::getStatus, statusValue);
        }
        // 最近创建的任务排在前面；id 作为创建时间相同时的稳定排序
        wrapper.orderByDesc(IntegrationJob::getCreatedAt).orderByDesc(IntegrationJob::getId);
        return PageResult.of(jobMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /**
     * 按 ID 查询集成任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws BusinessException 任务不存在时返回 404
     */
    @Transactional(readOnly = true)
    public IntegrationJob getJob(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, "任务ID不合法");
        }
        IntegrationJob job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(404, "集成任务不存在: " + id);
        }
        return job;
    }

    /**
     * 创建集成任务
     * <p>
     * 校验任务编码、任务名称、源端类型、目标端类型必填；job_code 唯一（包含已逻辑删除的任务，
     * 与数据库唯一索引保持一致）；未指定状态时默认 DRAFT。
     *
     * @param request 任务配置
     * @return 创建后的任务（含自增 ID）
     */
    @Transactional
    public IntegrationJob createJob(IntegrationJob request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String jobCode = requireText(request.getJobCode(), "任务编码", 64);
        String jobName = requireText(request.getJobName(), "任务名称", 128);
        String sourceType = requireText(request.getSourceType(), "源端类型", 32);
        String targetType = requireText(request.getTargetType(), "目标端类型", 32);
        String status = normalizeStatus(request.getStatus());
        if (status == null) {
            status = IntegrationJob.STATUS_DRAFT;
        }
        if (jobMapper.countByJobCodeIncludingDeleted(jobCode) > 0) {
            throw new BusinessException(409, "任务编码已存在: " + jobCode);
        }

        IntegrationJob job = new IntegrationJob();
        job.setJobCode(jobCode);
        job.setJobName(jobName);
        job.setSourceType(sourceType);
        job.setSourceConfig(encryptConfigPassword(request.getSourceConfig()));
        job.setTargetType(targetType);
        job.setTargetConfig(encryptConfigPassword(request.getTargetConfig()));
        job.setSyncMode(optionalText(request.getSyncMode(), "同步模式", 32));
        job.setStatus(status);

        LocalDateTime now = LocalDateTime.now();
        String operator = currentUser();
        job.setCreatedAt(now);
        job.setCreatedBy(operator);
        job.setUpdatedAt(now);
        job.setUpdatedBy(operator);
        // 明确忽略客户端传入的 id / 删除标记，避免伪造主键或写入已删除数据
        job.setId(null);
        job.setDeletedAt(null);

        try {
            jobMapper.insert(job);
        } catch (DuplicateKeyException e) {
            // 并发创建时由数据库唯一索引兜底，转换为可读的业务异常
            throw new BusinessException(409, "任务编码已存在: " + jobCode);
        }
        log.info("集成任务创建成功: id={}, jobCode={}", job.getId(), job.getJobCode());
        return job;
    }

    /**
     * 更新集成任务
     * <p>
     * 允许更新任务名称、源端/目标端类型与配置、同步模式和状态；
     * jobCode 一经创建不允许修改。请求中为 null 的字段保持原值。
     *
     * @param id      任务 ID
     * @param request 待更新字段
     * @return 更新后的任务
     */
    @Transactional
    public IntegrationJob updateJob(Long id, IntegrationJob request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        IntegrationJob job = getJob(id);
        String submittedCode = trimToNull(request.getJobCode());
        if (submittedCode != null && !submittedCode.equals(job.getJobCode())) {
            throw new BusinessException(400, "任务编码不允许修改");
        }
        if (request.getJobName() != null) {
            job.setJobName(requireText(request.getJobName(), "任务名称", 128));
        }
        if (request.getSourceType() != null) {
            job.setSourceType(requireText(request.getSourceType(), "源端类型", 32));
        }
        if (request.getSourceConfig() != null) {
            job.setSourceConfig(encryptConfigPassword(request.getSourceConfig()));
        }
        if (request.getTargetType() != null) {
            job.setTargetType(requireText(request.getTargetType(), "目标端类型", 32));
        }
        if (request.getTargetConfig() != null) {
            job.setTargetConfig(encryptConfigPassword(request.getTargetConfig()));
        }
        if (request.getSyncMode() != null) {
            job.setSyncMode(optionalText(request.getSyncMode(), "同步模式", 32));
        }
        if (request.getStatus() != null) {
            job.setStatus(normalizeStatus(request.getStatus()));
        }
        job.setUpdatedAt(LocalDateTime.now());
        job.setUpdatedBy(currentUser());
        jobMapper.updateById(job);
        log.info("集成任务更新成功: id={}, jobCode={}", job.getId(), job.getJobCode());
        return job;
    }

    /**
     * 逻辑删除集成任务（@TableLogic 只写 deleted_at，不物理删除配置）
     *
     * @param id 任务 ID
     */
    @Transactional
    public void deleteJob(Long id) {
        IntegrationJob job = getJob(id);
        int affected = jobMapper.deleteById(job.getId());
        if (affected == 0) {
            throw new BusinessException(404, "集成任务不存在: " + id);
        }
        log.info("集成任务已逻辑删除: id={}, jobCode={}", job.getId(), job.getJobCode());
    }

    /**
     * 切换任务启用/停用状态
     * <p>
     * 只允许 ENABLED / DISABLED，其他状态（含 RUNNING/FAILED）返回 400。
     *
     * @param id     任务 ID
     * @param status 目标状态
     * @return 更新后的任务
     */
    @Transactional
    public IntegrationJob changeStatus(Long id, String status) {
        IntegrationJob job = getJob(id);
        String targetStatus = requireSwitchableStatus(status);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentUser();

        LambdaUpdateWrapper<IntegrationJob> update = new LambdaUpdateWrapper<>();
        update.eq(IntegrationJob::getId, id)
                .set(IntegrationJob::getStatus, targetStatus)
                .set(IntegrationJob::getUpdatedAt, now)
                .set(IntegrationJob::getUpdatedBy, operator);
        if (jobMapper.update(null, update) == 0) {
            throw new BusinessException(404, "集成任务不存在: " + id);
        }
        log.info("集成任务状态切换: id={}, jobCode={}, {} -> {}",
                id, job.getJobCode(), job.getStatus(), targetStatus);
        return getJob(id);
    }

    /**
     * 触发任务（异步执行）
     * <p>
     * 事务内校验任务类型/同步模式、对任务行加锁并创建一条 PENDING 运行记录；
     * 事务提交后把真实 JDBC 同步提交到线程池，接口立即返回 accepted=true、runId、
     * status=ACCEPTED，不阻塞请求。同一任务已有 PENDING/RUNNING 记录时返回 409。
     *
     * @param id 任务 ID
     * @return 触发受理结果
     */
    @Transactional
    public Map<String, Object> triggerJob(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, "任务ID不合法");
        }
        IntegrationJob job = getJob(id);
        // 不支持的源端/目标端类型与 INCREMENTAL 模式在创建运行记录前明确报错
        executionService.validateSupported(job);

        // 任务行锁：串行化同一任务的 trigger，保证并发检查与 run_no 分配一致
        if (jobMapper.lockById(id) == null) {
            throw new BusinessException(404, "集成任务不存在: " + id);
        }

        LambdaQueryWrapper<IntegrationRun> activeQuery = new LambdaQueryWrapper<>();
        activeQuery.eq(IntegrationRun::getJobId, id)
                .in(IntegrationRun::getStatus,
                        IntegrationRun.STATUS_PENDING, IntegrationRun.STATUS_RUNNING);
        Long activeRuns = runMapper.selectCount(activeQuery);
        if (activeRuns != null && activeRuns > 0) {
            throw new BusinessException(409, "任务已有等待执行或正在执行的运行记录，禁止并发执行");
        }

        Integer maxRunNo = runMapper.selectMaxRunNo(id);
        int runNo = maxRunNo == null ? 1 : maxRunNo + 1;
        LocalDateTime now = LocalDateTime.now();
        IntegrationRun run = new IntegrationRun();
        run.setJobId(id);
        run.setRunNo(runNo);
        run.setStatus(IntegrationRun.STATUS_PENDING);
        run.setRowsRead(0L);
        run.setRowsWritten(0L);
        run.setCreatedAt(now);
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException e) {
            // integration_run 活跃记录唯一索引兜底并发竞态
            throw new BusinessException(409, "任务已有等待执行或正在执行的运行记录，禁止并发执行");
        }

        LambdaUpdateWrapper<IntegrationJob> update = new LambdaUpdateWrapper<>();
        update.eq(IntegrationJob::getId, id)
                .set(IntegrationJob::getLastRunAt, now)
                .set(IntegrationJob::getLastError, null)
                .set(IntegrationJob::getUpdatedAt, now)
                .set(IntegrationJob::getUpdatedBy, currentUser());
        if (jobMapper.update(null, update) == 0) {
            throw new BusinessException(404, "集成任务不存在: " + id);
        }

        // 事务提交后才提交线程池，确保执行线程一定能读到 PENDING 运行记录
        scheduleExecution(run.getId(), id);
        log.info("集成任务触发受理: id={}, jobCode={}, runId={}, runNo={}",
                id, job.getJobCode(), run.getId(), runNo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("runId", run.getId());
        result.put("jobId", id);
        result.put("runNo", runNo);
        result.put("status", "ACCEPTED");
        return result;
    }

    /**
     * 事务提交后把运行记录提交到同步线程池
     * <p>
     * 若线程池拒绝（队列满/正在关闭），立即把 PENDING 运行记录和任务置为 FAILED，
     * 避免运行记录永远停留在 PENDING。
     *
     * @param runId 运行记录 ID
     * @param jobId 集成任务 ID
     */
    private void scheduleExecution(Long runId, Long jobId) {
        Runnable submit = () -> {
            try {
                integrationTaskExecutor.execute(() -> executionService.executeRun(runId, jobId));
            } catch (RuntimeException e) {
                try {
                    executionService.markFailedBeforeStart(runId, jobId,
                            "执行线程池拒绝任务: " + e.getMessage());
                } catch (RuntimeException markError) {
                    log.error("标记集成运行记录提交失败时出错: runId={}, jobId={}",
                            runId, jobId, markError);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            // 非事务上下文（如内部直调）直接提交，接口层仍由 @Transactional 保证走 afterCommit
            submit.run();
        }
    }

    /**
     * 加密端点配置 JSON 根字段 password 后落库
     * <p>
     * 只处理根字段 {@code password}，其余字段值原样保留；null/JSON null、空白值以及
     * 已带 {@code ENC1:} 前缀的密文不做处理，避免前端回传密文时重复加密。配置不是
     * 合法 JSON 时保持原有落库行为（原样保存）；加密本身失败则向上抛出，不降级为明文。
     *
     * @param rawConfig 原始配置 JSON
     * @return 加密后的配置 JSON；无需加密或解析失败时返回规范化后的原值
     */
    private String encryptConfigPassword(String rawConfig) {
        String config = trimToNull(rawConfig);
        if (config == null) {
            return null;
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(config);
        } catch (RuntimeException e) {
            // 仅记录异常类型，避免把配置内容（含密码）带入日志
            log.warn("集成任务配置 JSON 解析失败，跳过密码加密: errorType={}",
                    e.getClass().getSimpleName());
            return config;
        }
        if (json == null || json.isEmpty()) {
            return config;
        }
        Object value = json.get("password");
        if (value == null || value instanceof JSONNull) {
            return config;
        }
        String plain = String.valueOf(value);
        if (plain.isBlank() || plain.startsWith(CredentialCipher.PREFIX)) {
            return config;
        }
        json.set("password", credentialCipher.encrypt(plain));
        return json.toString();
    }

    /**
     * 校验必填文本字段：去空格后不能为空，且不能超过指定长度
     *
     * @param value     原始值
     * @param fieldName 字段中文名
     * @param maxLength 最大长度
     * @return 去除首尾空格后的值
     */
    private static String requireText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException(400, fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    /**
     * 校验可选文本字段：为空返回 null，否则去空格并检查长度
     *
     * @param value     原始值
     * @param fieldName 字段中文名
     * @param maxLength 最大长度
     * @return 规范化后的值或 null
     */
    private static String optionalText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new BusinessException(400, fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    /**
     * 去除首尾空格，空字符串转为 null
     *
     * @param value 原始值
     * @return 规范化后的值或 null
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 规范化并校验任务状态（允许全部状态值）
     *
     * @param status 原始状态
     * @return 大写状态；入参为空时返回 null
     */
    private static String normalizeStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException(400,
                    "非法状态: " + status + "，仅支持 DRAFT/ENABLED/DISABLED/RUNNING/FAILED");
        }
        return normalized;
    }

    /**
     * 规范化并校验状态切换接口的目标状态（只允许 ENABLED / DISABLED）
     *
     * @param status 原始状态
     * @return 大写状态
     */
    private static String requireSwitchableStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            throw new BusinessException(400, "状态不能为空，仅支持 ENABLED/DISABLED");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!SWITCHABLE_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "非法状态: " + status + "，仅支持 ENABLED/DISABLED");
        }
        return normalized;
    }

    /**
     * 获取当前登录用户标识；无登录上下文（如内部调用/本地调试）时回退为 system
     *
     * @return 用户标识
     */
    private static String currentUser() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? "system" : String.valueOf(loginId);
        } catch (Exception e) {
            // 无请求上下文或未登录时不影响任务管理接口，使用系统账号记录审计字段
            return "system";
        }
    }
}
