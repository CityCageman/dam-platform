package com.dam.integration.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.integration.entity.IntegrationJob;
import com.dam.integration.repository.IntegrationJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据集成任务管理服务
 * <p>
 * 负责 integration_job 的分页查询、详情、创建、更新、逻辑删除与状态切换。
 * 当前阶段只管理任务配置和状态，不实现真实的数据同步执行引擎：
 * trigger 接口只记录触发时间与提示，不改变任务状态，返回 accepted=true / taskCreated=false。
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

    /** 触发接口固定提示：执行引擎尚未实现 */
    private static final String EXECUTION_ENGINE_NOT_IMPLEMENTED = "execution engine not implemented";

    /** 集成任务 Mapper */
    private final IntegrationJobMapper jobMapper;

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
        job.setSourceConfig(trimToNull(request.getSourceConfig()));
        job.setTargetType(targetType);
        job.setTargetConfig(trimToNull(request.getTargetConfig()));
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
            job.setSourceConfig(trimToNull(request.getSourceConfig()));
        }
        if (request.getTargetType() != null) {
            job.setTargetType(requireText(request.getTargetType(), "目标端类型", 32));
        }
        if (request.getTargetConfig() != null) {
            job.setTargetConfig(trimToNull(request.getTargetConfig()));
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
     * 触发任务
     * <p>
     * 当前未实现同步执行引擎：仅将任务状态标记为 RUNNING、记录最近触发时间，
     * 并返回 accepted=true / taskCreated=false，绝不伪造同步完成结果。
     *
     * @param id 任务 ID
     * @return 触发受理结果
     */
    @Transactional
    public Map<String, Object> triggerJob(Long id) {
        IntegrationJob job = getJob(id);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentUser();

        // 没有执行引擎时不能把任务置为 RUNNING，否则会永远停在运行中；
        // 只记录触发时间与提示，等待后续执行引擎接管。
        LambdaUpdateWrapper<IntegrationJob> update = new LambdaUpdateWrapper<>();
        update.eq(IntegrationJob::getId, id)
                .set(IntegrationJob::getLastRunAt, now)
                .set(IntegrationJob::getLastError, EXECUTION_ENGINE_NOT_IMPLEMENTED)
                .set(IntegrationJob::getUpdatedAt, now)
                .set(IntegrationJob::getUpdatedBy, operator);
        if (jobMapper.update(null, update) == 0) {
            throw new BusinessException(404, "集成任务不存在: " + id);
        }
        log.info("集成任务已受理触发（执行引擎未实现）: id={}, jobCode={}", id, job.getJobCode());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("taskCreated", false);
        result.put("message", EXECUTION_ENGINE_NOT_IMPLEMENTED);
        result.put("jobId", id);
        result.put("status", job.getStatus());
        return result;
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
