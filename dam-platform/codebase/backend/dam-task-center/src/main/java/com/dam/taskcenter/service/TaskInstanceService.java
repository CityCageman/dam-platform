package com.dam.taskcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.taskcenter.dto.TaskStatusRequest;
import com.dam.taskcenter.entity.TaskDefinition;
import com.dam.taskcenter.entity.TaskInstance;
import com.dam.taskcenter.repository.TaskDefinitionMapper;
import com.dam.taskcenter.repository.TaskInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class TaskInstanceService {

    /** 单页上限，避免 pageSize 过大拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

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

    /** 任务定义 Mapper（校验来源定义并加行锁） */
    private final TaskDefinitionMapper taskDefinitionMapper;

    /** 任务实例 Mapper */
    private final TaskInstanceMapper taskInstanceMapper;

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
