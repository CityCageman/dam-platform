package com.dam.taskcenter.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.taskcenter.dto.TaskDefinitionRequest;
import com.dam.taskcenter.entity.TaskDefinition;
import com.dam.taskcenter.repository.TaskDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * 任务定义管理服务。
 * <p>
 * 负责任务定义的分页查询、创建、更新与逻辑删除。任务编码全局唯一且创建后
 * 不允许修改；任务类型限定为 INTEGRATION/SCHEDULER/DEV/QUALITY/AI，
 * 状态限定为 DRAFT/ENABLED/DISABLED。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class TaskDefinitionService {

    /** 单页上限，避免 pageSize 过大拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 关键字最大长度，避免超长 LIKE 影响查询 */
    private static final int MAX_KEYWORD_LENGTH = 100;

    /** 任务配置 JSON 最大长度 */
    private static final int MAX_CONFIG_LENGTH = 20000;

    /** 合法任务类型 */
    private static final Set<String> TASK_TYPES = Set.of(
            "INTEGRATION", "SCHEDULER", "DEV", "QUALITY", "AI");

    /** 合法任务状态 */
    private static final Set<String> TASK_STATUSES = Set.of(
            "DRAFT", "ENABLED", "DISABLED");

    /** 任务定义 Mapper */
    private final TaskDefinitionMapper taskDefinitionMapper;

    /**
     * 分页查询任务定义。
     *
     * @param keyword  关键字（匹配 taskCode/taskName/sourceService，可空）
     * @param taskType 任务类型过滤（可空）
     * @param status   状态过滤（可空）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数（1~100）
     * @return 分页结果
     */
    public PageResult<TaskDefinition> page(String keyword, String taskType,
                                           String status, Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null ? 20 : Math.clamp(pageSize, 1, MAX_PAGE_SIZE);

        LambdaQueryWrapper<TaskDefinition> wrapper = new LambdaQueryWrapper<>();
        if (hasText(keyword)) {
            String kw = keyword.trim();
            if (kw.length() > MAX_KEYWORD_LENGTH) {
                kw = kw.substring(0, MAX_KEYWORD_LENGTH);
            }
            String matched = kw;
            wrapper.and(w -> w.like(TaskDefinition::getTaskCode, matched)
                    .or().like(TaskDefinition::getTaskName, matched)
                    .or().like(TaskDefinition::getSourceService, matched));
        }
        if (hasText(taskType)) {
            wrapper.eq(TaskDefinition::getTaskType, normalizeTaskType(taskType));
        }
        if (hasText(status)) {
            wrapper.eq(TaskDefinition::getStatus, normalizeStatus(status));
        }
        wrapper.orderByDesc(TaskDefinition::getUpdatedAt)
                .orderByDesc(TaskDefinition::getId);
        return PageResult.of(taskDefinitionMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /**
     * 查询任务定义详情。
     *
     * @param id 任务定义ID
     * @return 任务定义
     */
    public TaskDefinition getById(Long id) {
        return requireDefinition(id);
    }

    /**
     * 创建任务定义。
     * <p>
     * 校验 taskCode/taskName/taskType/sourceService；taskCode 全局唯一（包含已逻辑
     * 删除记录，避免复用编码触发数据库唯一约束异常）；status 缺省 DRAFT。
     *
     * @param request 创建请求
     * @return 创建后的任务定义（含自增ID）
     */
    @Transactional
    public TaskDefinition create(TaskDefinitionRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String taskCode = requireText(request.getTaskCode(), "taskCode", 64);
        String taskName = requireText(request.getTaskName(), "taskName", 200);
        String taskType = normalizeTaskType(request.getTaskType());
        String sourceService = requireText(request.getSourceService(), "sourceService", 64);
        String status = hasText(request.getStatus())
                ? normalizeStatus(request.getStatus()) : "DRAFT";
        String config = normalizeConfig(request.getConfig());
        String description = optionalText(request.getDescription(), "description", 2000);

        // taskCode 唯一性校验：包含已逻辑删除的记录（数据库唯一索引不区分删除状态）
        if (taskDefinitionMapper.countByTaskCodeIncludingDeleted(taskCode) > 0) {
            throw new BusinessException(409, "taskCode 已存在：" + taskCode);
        }

        String operator = currentUser();
        LocalDateTime now = LocalDateTime.now();
        TaskDefinition definition = new TaskDefinition();
        definition.setTaskCode(taskCode);
        definition.setTaskName(taskName);
        definition.setTaskType(taskType);
        definition.setSourceService(sourceService);
        definition.setStatus(status);
        definition.setConfig(config);
        definition.setDescription(description);
        definition.setCreatedAt(now);
        definition.setCreatedBy(operator);
        definition.setUpdatedAt(now);
        definition.setUpdatedBy(operator);
        try {
            taskDefinitionMapper.insert(definition);
        } catch (DuplicateKeyException e) {
            // 并发创建时由数据库唯一索引兜底
            throw new BusinessException(409, "taskCode 已存在：" + taskCode);
        }
        return definition;
    }

    /**
     * 回调 start 自动登记任务定义。
     * <p>
     * 使用独立事务（REQUIRES_NEW）：并发创建命中 taskCode 唯一索引时，内层事务回滚，
     * 调用方可在原事务中按生成编码重新查询已提交定义，避免整个 start 事务被标记为
     * aborted。定义状态固定为 ENABLED，创建/更新人均为 system，描述固定为
     * “TaskCenter 自动登记”。
     *
     * @param taskCode      自动生成的稳定任务编码（不超过 64）
     * @param taskName      任务名称（取回调标题，不超过 200）
     * @param taskType      任务类型
     * @param sourceService 来源服务
     * @return 已创建的任务定义（含自增ID）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskDefinition autoRegister(String taskCode, String taskName,
                                       String taskType, String sourceService) {
        String code = requireText(taskCode, "taskCode", 64);
        String name = requireText(taskName, "taskName", 200);
        String type = normalizeTaskType(taskType);
        String source = requireText(sourceService, "sourceService", 64);

        // 与 create 一致：唯一性校验包含已逻辑删除记录，避免复用编码触发唯一约束
        if (taskDefinitionMapper.countByTaskCodeIncludingDeleted(code) > 0) {
            throw new BusinessException(409, "taskCode 已存在：" + code);
        }

        LocalDateTime now = LocalDateTime.now();
        TaskDefinition definition = new TaskDefinition();
        definition.setTaskCode(code);
        definition.setTaskName(name);
        definition.setTaskType(type);
        definition.setSourceService(source);
        definition.setStatus("ENABLED");
        definition.setDescription("TaskCenter 自动登记");
        definition.setCreatedAt(now);
        definition.setCreatedBy("system");
        definition.setUpdatedAt(now);
        definition.setUpdatedBy("system");
        try {
            taskDefinitionMapper.insert(definition);
        } catch (DuplicateKeyException e) {
            // 并发自动登记由数据库唯一索引兜底；内层事务回滚后由调用方重新查询
            throw new BusinessException(409, "taskCode 已存在：" + code);
        }
        return definition;
    }

    /**
     * 更新任务定义。
     * <p>
     * 不允许修改 taskCode；未传字段保持原值。status/taskType/config 传入时重新校验。
     *
     * @param id      任务定义ID
     * @param request 更新请求
     * @return 更新后的任务定义
     */
    @Transactional
    public TaskDefinition update(Long id, TaskDefinitionRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        TaskDefinition definition = requireDefinition(id);

        if (request.getTaskCode() != null
                && !request.getTaskCode().trim().equals(definition.getTaskCode())) {
            throw new BusinessException(400, "不允许修改 taskCode");
        }
        if (request.getTaskName() != null) {
            definition.setTaskName(requireText(request.getTaskName(), "taskName", 200));
        }
        if (request.getTaskType() != null) {
            definition.setTaskType(normalizeTaskType(request.getTaskType()));
        }
        if (request.getSourceService() != null) {
            definition.setSourceService(
                    requireText(request.getSourceService(), "sourceService", 64));
        }
        if (request.getStatus() != null) {
            definition.setStatus(normalizeStatus(request.getStatus()));
        }
        if (request.getConfig() != null) {
            definition.setConfig(normalizeConfig(request.getConfig()));
        }
        if (request.getDescription() != null) {
            definition.setDescription(
                    optionalText(request.getDescription(), "description", 2000));
        }
        definition.setUpdatedAt(LocalDateTime.now());
        definition.setUpdatedBy(currentUser());
        if (taskDefinitionMapper.updateById(definition) == 0) {
            // 并发删除等场景下避免返回已不存在的定义
            throw new BusinessException(404, "任务定义不存在：" + id);
        }
        return definition;
    }

    /**
     * 逻辑删除任务定义。
     * <p>
     * 历史任务实例与日志保留，不做级联删除。
     *
     * @param id 任务定义ID
     */
    @Transactional
    public void delete(Long id) {
        requireDefinition(id);
        // @TableLogic 自动将 deleted_at 置为 now()
        if (taskDefinitionMapper.deleteById(id) == 0) {
            throw new BusinessException(404, "任务定义不存在：" + id);
        }
    }

    /**
     * 按 ID 查询未删除的任务定义，不存在时抛出 404 业务异常。
     *
     * @param id 任务定义ID
     * @return 任务定义
     */
    private TaskDefinition requireDefinition(Long id) {
        if (id == null) {
            throw new BusinessException(400, "任务定义ID不能为空");
        }
        TaskDefinition definition = taskDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BusinessException(404, "任务定义不存在：" + id);
        }
        return definition;
    }

    /**
     * 归一化并校验任务类型。
     *
     * @param raw 原始任务类型
     * @return 大写任务类型
     */
    private static String normalizeTaskType(String raw) {
        String value = requireText(raw, "taskType", 32).toUpperCase(Locale.ROOT);
        if (!TASK_TYPES.contains(value)) {
            throw new BusinessException(400,
                    "taskType 不合法，仅支持 INTEGRATION/SCHEDULER/DEV/QUALITY/AI：" + raw);
        }
        return value;
    }

    /**
     * 归一化并校验任务状态。
     *
     * @param raw 原始状态
     * @return 大写状态
     */
    private static String normalizeStatus(String raw) {
        String value = requireText(raw, "status", 16).toUpperCase(Locale.ROOT);
        if (!TASK_STATUSES.contains(value)) {
            throw new BusinessException(400,
                    "status 不合法，仅支持 DRAFT/ENABLED/DISABLED：" + raw);
        }
        return value;
    }

    /**
     * 将任务配置转换为合法 JSON 文本（jsonb）。
     * <p>
     * 支持请求体直接传 JSON 对象/数组，也支持传 JSON 字符串；空值落库为 NULL。
     *
     * @param config 任务配置
     * @return JSON 文本或 null
     */
    private static String normalizeConfig(Object config) {
        if (config == null) {
            return null;
        }
        String json;
        if (config instanceof String value) {
            json = value.trim();
        } else {
            json = JSONUtil.toJsonStr(config);
        }
        if (json == null || json.isEmpty()) {
            return null;
        }
        if (json.length() > MAX_CONFIG_LENGTH) {
            throw new BusinessException(400,
                    "config 长度不能超过 " + MAX_CONFIG_LENGTH);
        }
        if (!JSONUtil.isTypeJSON(json)) {
            throw new BusinessException(400, "config 必须是合法的 JSON");
        }
        return json;
    }

    /**
     * 校验必填文本并返回去空格后的值。
     *
     * @param raw     原始值
     * @param field   字段名
     * @param maxLen  最大长度
     * @return 去空格后的文本
     */
    private static String requireText(String raw, String field, int maxLen) {
        if (!hasText(raw)) {
            throw new BusinessException(400, field + " 不能为空");
        }
        String value = raw.trim();
        if (value.length() > maxLen) {
            throw new BusinessException(400, field + " 长度不能超过 " + maxLen);
        }
        return value;
    }

    /**
     * 校验可选文本；空白值归一为 null。
     *
     * @param raw    原始值
     * @param field  字段名
     * @param maxLen 最大长度
     * @return 去空格后的文本或 null
     */
    private static String optionalText(String raw, String field, int maxLen) {
        if (!hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > maxLen) {
            throw new BusinessException(400, field + " 长度不能超过 " + maxLen);
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

    /**
     * 当前操作人；未登录或解析失败时使用 system。
     *
     * @return 操作人标识
     */
    private static String currentUser() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? "system" : String.valueOf(loginId);
        } catch (Exception e) {
            return "system";
        }
    }
}
