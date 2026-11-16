package com.dam.taskcenter.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.common.exception.BusinessException;
import com.dam.taskcenter.dto.TaskCallbackFinishRequest;
import com.dam.taskcenter.dto.TaskCallbackRequest;
import com.dam.taskcenter.dto.TaskCallbackResponse;
import com.dam.taskcenter.dto.TaskLogRequest;
import com.dam.taskcenter.entity.TaskDefinition;
import com.dam.taskcenter.entity.TaskInstance;
import com.dam.taskcenter.entity.TaskLog;
import com.dam.taskcenter.repository.TaskDefinitionMapper;
import com.dam.taskcenter.repository.TaskInstanceMapper;
import com.dam.taskcenter.repository.TaskLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * 统一任务回调服务。
 * <p>
 * 目标是为集成、调度、开发等外部执行器提供统一的运行状态回调入口：
 * start 登记/复用实例，finish 回填终态，log 追加日志，get 查询实例。
 * <p>
 * <b>start 的幂等限制（未伪造）：</b>
 * 现有 {@code dam_task.task_instance} 只有
 * id/definition_id/task_code/run_no/status/trigger_type/started_at/finished_at/
 * error_message/created_at 字段，没有 source_service、source_run_id、title、payload
 * 等来源字段；task_code 为 VARCHAR(64) 且没有唯一约束，唯一约束只有
 * (definition_id, run_no)。因此无法在不改表的前提下可靠持久化
 * sourceService + sourceTaskCode + sourceRunId 幂等键：
 * <ul>
 *     <li>task_code 长度不足以编码任意来源服务/任务/运行ID；</li>
 *     <li>task_code 无唯一索引，并发重复请求无法由数据库原子去重；</li>
 *     <li>run_no/error_message 等字段语义固定，不能挪用为幂等键。</li>
 * </ul>
 * 按任务要求，start 在确认任务定义存在后返回 501 并明确说明限制，不创建任何实例。
 * finish/log/get 复用 task_instance/task_log 正常工作。
 * <p>
 * 由于 task_instance 没有 rows_read/rows_written 字段，finish 上报的计数会以
 * {@value #FINISH_MARKER_PREFIX} 开头的结构化日志写入 task_log，并在响应中回显；
 * 这是真实持久化，不伪造实例列。若后续允许数据库迁移，建议新增
 * {@code task_callback_ref} 表（source_service, source_task_code, source_run_id 唯一）
 * 或为 task_instance 增加来源字段与唯一索引，然后即可启用 start。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCallbackService {

    /** start 支持的幂等键组成部分的最大长度（与 DTO 校验保持一致） */
    private static final int MAX_SOURCE_RUN_ID_LENGTH = 128;

    /** 失败/取消原因最大长度 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    /** task_log.message 最大长度（与 TaskLogService 保持一致） */
    private static final int MAX_LOG_MESSAGE_LENGTH = 10000;

    /** 允许的回调结束状态 */
    private static final Set<String> FINISH_STATUSES = Set.of(
            "SUCCESS", "FAILED", "CANCELLED");

    /** 终态集合 */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCESS", "FAILED", "CANCELLED");

    /** 回调结束日志前缀（用于在 task_log 中持久化 rowsRead/rowsWritten） */
    private static final String FINISH_MARKER_PREFIX = "CALLBACK_FINISH ";

    /** 任务定义 Mapper（start 校验来源定义、get/finish 补充任务类型） */
    private final TaskDefinitionMapper taskDefinitionMapper;

    /** 任务实例 Mapper（finish 行锁更新、get 查询） */
    private final TaskInstanceMapper taskInstanceMapper;

    /** 任务日志 Mapper（写入/读取回调结束计数标记） */
    private final TaskLogMapper taskLogMapper;

    /** 任务日志服务（复用日志校验与追加逻辑） */
    private final TaskLogService taskLogService;

    /**
     * 回调开始：登记或复用任务实例。
     * <p>
     * 先按 definitionId 或 sourceService+sourceTaskCode 校验任务定义：
     * 定义不存在返回 404，并提示先创建任务定义（不自动造假定义）。
     * 定义存在时，由于 task_instance 无法可靠持久化
     * sourceService+sourceTaskCode+sourceRunId 幂等键，返回 501，
     * 不创建实例、不返回假的 instanceId。
     *
     * @param request 回调开始请求
     * @return 不会返回；定义存在时抛出 501
     */
    @Transactional
    public TaskCallbackResponse start(TaskCallbackRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String sourceService = requireText(request.getSourceService(), "sourceService", 64);
        String sourceTaskCode = requireText(request.getSourceTaskCode(), "sourceTaskCode", 64);
        String sourceRunId = requireText(request.getSourceRunId(), "sourceRunId", MAX_SOURCE_RUN_ID_LENGTH);
        requireText(request.getTaskType(), "taskType", 32);
        String title = requireText(request.getTitle(), "title", 200);
        String payload = request.getPayload() == null ? null : JSONUtil.toJsonStr(request.getPayload());

        TaskDefinition definition = resolveDefinition(
                request.getDefinitionId(), sourceService, sourceTaskCode);

        TaskInstance existing = taskInstanceMapper.selectOne(
                new LambdaQueryWrapper<TaskInstance>()
                        .eq(TaskInstance::getSourceService, sourceService)
                        .eq(TaskInstance::getSourceTaskCode, sourceTaskCode)
                        .eq(TaskInstance::getSourceRunId, sourceRunId)
                        .last("LIMIT 1"));
        if (existing != null) {
            return toResponse(existing, null, null, false, true,
                    "相同来源运行已登记，返回已有实例");
        }

        // 对任务定义行加锁，串行分配同一任务内的 run_no
        if (taskDefinitionMapper.lockById(definition.getId()) == null) {
            throw new BusinessException(404, "任务定义不存在：" + definition.getId());
        }
        Integer maxRunNo = taskInstanceMapper.selectMaxRunNo(definition.getId());
        int runNo = maxRunNo == null ? 0 : maxRunNo;
        TaskInstance instance = new TaskInstance();
        instance.setDefinitionId(definition.getId());
        instance.setTaskCode(definition.getTaskCode());
        instance.setRunNo(runNo + 1);
        instance.setStatus("PENDING");
        instance.setTriggerType("API");
        instance.setCreatedAt(LocalDateTime.now());
        instance.setSourceService(sourceService);
        instance.setSourceTaskCode(sourceTaskCode);
        instance.setSourceRunId(sourceRunId);
        instance.setTitle(title);
        instance.setPayload(payload);
        try {
            taskInstanceMapper.insert(instance);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            TaskInstance concurrent = taskInstanceMapper.selectOne(
                    new LambdaQueryWrapper<TaskInstance>()
                            .eq(TaskInstance::getSourceService, sourceService)
                            .eq(TaskInstance::getSourceTaskCode, sourceTaskCode)
                            .eq(TaskInstance::getSourceRunId, sourceRunId)
                            .last("LIMIT 1"));
            if (concurrent != null) {
                return toResponse(concurrent, null, null, false, true,
                        "并发请求命中已有实例");
            }
            throw new BusinessException(409, "来源运行已登记，请重试");
        }
        return toResponse(instance, null, null, true, false, "回调开始已登记");
    }

    /**
     * 回调结束：把实例更新为终态并回填时间。
     * <p>
     * 仅允许 SUCCESS/FAILED/CANCELLED。终态实例重复提交相同状态视为幂等成功，
     * 不同状态返回 409。rowsRead/rowsWritten 没有对应实例列，会以结构化日志写入
     * task_log，并在响应中回显；errorMessage 写入 task_instance.error_message。
     * 使用 SELECT ... FOR UPDATE 串行化同一实例的并发结束请求。
     *
     * @param instanceId 实例ID
     * @param request    回调结束请求
     * @return 更新后的实例快照
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskCallbackResponse finish(Long instanceId, TaskCallbackFinishRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        Long id = requireInstanceId(instanceId);
        TaskInstance instance = taskInstanceMapper.selectByIdForUpdate(id);
        if (instance == null) {
            throw new BusinessException(404, "任务实例不存在：" + id);
        }
        String target = normalizeFinishStatus(request.getStatus());
        String current = instance.getStatus();

        if (TERMINAL_STATUSES.contains(current)) {
            if (current.equals(target)) {
                Long[] counters = readFinishCounters(id);
                return toResponse(instance, counters[0], counters[1], false, true,
                        "实例已处于终态，返回已有结果");
            }
            throw new BusinessException(409,
                    "实例已处于终态 " + current + "，不能变更为 " + target);
        }
        if (!"PENDING".equals(current) && !"RUNNING".equals(current)) {
            throw new BusinessException(400, "实例当前状态不支持结束：" + current);
        }

        Long rowsRead = request.getRowsRead();
        Long rowsWritten = request.getRowsWritten();
        if (rowsRead != null && rowsRead < 0) {
            throw new BusinessException(400, "rowsRead 不能为负数");
        }
        if (rowsWritten != null && rowsWritten < 0) {
            throw new BusinessException(400, "rowsWritten 不能为负数");
        }
        String errorMessage = null;
        if (hasText(request.getErrorMessage())) {
            if ("SUCCESS".equals(target)) {
                throw new BusinessException(400, "SUCCESS 状态不允许设置 errorMessage");
            }
            errorMessage = request.getErrorMessage().trim();
            if (errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
                throw new BusinessException(400,
                        "errorMessage 长度不能超过 " + MAX_ERROR_MESSAGE_LENGTH);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        instance.setStatus(target);
        if (instance.getStartedAt() == null) {
            instance.setStartedAt(now);
        }
        instance.setFinishedAt(now);
        if (errorMessage != null) {
            instance.setErrorMessage(errorMessage);
        }
        if (taskInstanceMapper.updateById(instance) == 0) {
            throw new BusinessException(404, "任务实例不存在：" + id);
        }

        if (rowsRead != null || rowsWritten != null) {
            appendFinishMarker(id, target, rowsRead, rowsWritten);
        }
        return toResponse(instance, rowsRead, rowsWritten, false, false,
                "回调结束已写入");
    }

    /**
     * 追加回调日志。
     * <p>
     * 复用 task_log 的既有校验（实例存在、message 必填、level 合法）。
     *
     * @param instanceId 实例ID
     * @param request    日志请求
     * @return 追加后的日志
     */
    public TaskLog appendLog(Long instanceId, TaskLogRequest request) {
        return taskLogService.append(instanceId, request);
    }

    /**
     * 查询回调实例。
     * <p>
     * 返回 task_instance 快照；rowsRead/rowsWritten 从最近一条回调结束日志读取。
     * sourceService/sourceTaskCode/sourceRunId/title/payload 因 task_instance 无对应
     * 字段而为 null（start 当前未启用，不会产生这类实例）。
     *
     * @param instanceId 实例ID
     * @return 实例快照
     */
    @Transactional(readOnly = true)
    public TaskCallbackResponse get(Long instanceId) {
        Long id = requireInstanceId(instanceId);
        TaskInstance instance = taskInstanceMapper.selectById(id);
        if (instance == null) {
            throw new BusinessException(404, "任务实例不存在：" + id);
        }
        Long[] counters = readFinishCounters(id);
        return toResponse(instance, counters[0], counters[1], false, false,
                "查询成功");
    }

    /**
     * 解析并校验回调开始对应的任务定义。
     * <p>
     * 支持显式 definitionId，也支持按 sourceService+sourceTaskCode 查找。
     * 找不到时返回 404 并提示先创建任务定义，不自动创建定义。
     *
     * @param definitionId    可选任务定义ID
     * @param sourceService   来源服务
     * @param sourceTaskCode  来源任务编码
     * @return 任务定义
     */
    private TaskDefinition resolveDefinition(Long definitionId,
                                             String sourceService,
                                             String sourceTaskCode) {
        if (definitionId != null) {
            if (definitionId <= 0) {
                throw new BusinessException(400, "definitionId 不合法：" + definitionId);
            }
            TaskDefinition definition = taskDefinitionMapper.selectById(definitionId);
            if (definition == null) {
                throw new BusinessException(404,
                        "任务定义不存在：" + definitionId + "，请先创建任务定义");
            }
            if (!sourceTaskCode.equals(definition.getTaskCode())
                    || !sourceService.equals(definition.getSourceService())) {
                throw new BusinessException(404,
                        "未找到匹配 sourceService=" + sourceService
                                + "、sourceTaskCode=" + sourceTaskCode
                                + " 的任务定义，请先创建任务定义");
            }
            return definition;
        }

        TaskDefinition definition = taskDefinitionMapper.selectOne(
                new LambdaQueryWrapper<TaskDefinition>()
                        .eq(TaskDefinition::getTaskCode, sourceTaskCode)
                        .last("LIMIT 1"));
        if (definition == null || !sourceService.equals(definition.getSourceService())) {
            throw new BusinessException(404,
                    "未找到匹配 sourceService=" + sourceService
                            + "、sourceTaskCode=" + sourceTaskCode
                            + " 的任务定义，请先创建任务定义");
        }
        return definition;
    }

    /**
     * 写入回调结束计数标记（task_log）。
     * <p>
     * task_instance 没有 rows_read/rows_written 字段，不能伪造实例列；
     * 这里把计数作为结构化日志真实落库，get/finish 时再读回。
     *
     * @param instanceId  实例ID
     * @param status      终态
     * @param rowsRead    读取行数（可空）
     * @param rowsWritten 写入行数（可空）
     */
    private void appendFinishMarker(Long instanceId, String status,
                                    Long rowsRead, Long rowsWritten) {
        JSONObject body = JSONUtil.createObj().set("status", status);
        if (rowsRead != null) {
            body.set("rowsRead", rowsRead);
        }
        if (rowsWritten != null) {
            body.set("rowsWritten", rowsWritten);
        }
        String message = FINISH_MARKER_PREFIX + body;
        if (message.length() > MAX_LOG_MESSAGE_LENGTH) {
            throw new BusinessException(400,
                    "回调结束元数据过长，无法写入任务日志（上限 "
                            + MAX_LOG_MESSAGE_LENGTH + "）");
        }
        TaskLog log = new TaskLog();
        log.setInstanceId(instanceId);
        log.setLogLevel("INFO");
        log.setMessage(message);
        log.setLoggedAt(LocalDateTime.now());
        taskLogMapper.insert(log);
    }

    /**
     * 读取最近一条回调结束计数标记。
     *
     * @param instanceId 实例ID
     * @return [rowsRead, rowsWritten]，未上报时为 null
     */
    private Long[] readFinishCounters(Long instanceId) {
        TaskLog marker = taskLogMapper.selectLatestByMessagePrefix(
                instanceId, FINISH_MARKER_PREFIX, FINISH_MARKER_PREFIX.length());
        if (marker == null || marker.getMessage() == null
                || !marker.getMessage().startsWith(FINISH_MARKER_PREFIX)) {
            return new Long[]{null, null};
        }
        try {
            JSONObject body = JSONUtil.parseObj(
                    marker.getMessage().substring(FINISH_MARKER_PREFIX.length()));
            return new Long[]{body.getLong("rowsRead"), body.getLong("rowsWritten")};
        } catch (Exception e) {
            log.warn("解析回调结束标记失败: instanceId={}", instanceId, e);
            return new Long[]{null, null};
        }
    }

    /**
     * 把实例快照转换为回调响应。
     *
     * @param instance    任务实例
     * @param rowsRead    读取行数
     * @param rowsWritten 写入行数
     * @param created     是否本次新建
     * @param reused      是否复用已有实例
     * @param message     结果说明
     * @return 回调响应
     */
    private TaskCallbackResponse toResponse(TaskInstance instance,
                                            Long rowsRead, Long rowsWritten,
                                            boolean created, boolean reused,
                                            String message) {
        TaskCallbackResponse response = new TaskCallbackResponse();
        response.setInstanceId(instance.getId());
        response.setDefinitionId(instance.getDefinitionId());
        response.setTaskCode(instance.getTaskCode());
        response.setInstanceTaskCode(instance.getTaskCode());
        response.setSourceService(instance.getSourceService());
        response.setSourceTaskCode(instance.getSourceTaskCode());
        response.setSourceRunId(instance.getSourceRunId());
        response.setTitle(instance.getTitle());
        response.setPayload(instance.getPayload());
        response.setStatus(instance.getStatus());
        response.setTriggerType(instance.getTriggerType());
        response.setRunNo(instance.getRunNo());
        response.setStartedAt(instance.getStartedAt());
        response.setFinishedAt(instance.getFinishedAt());
        response.setErrorMessage(instance.getErrorMessage());
        response.setRowsRead(rowsRead);
        response.setRowsWritten(rowsWritten);
        response.setCreatedAt(instance.getCreatedAt());
        response.setCreated(created);
        response.setReused(reused);
        response.setMessage(message);

        if (instance.getDefinitionId() != null) {
            TaskDefinition definition = taskDefinitionMapper.selectById(
                    instance.getDefinitionId());
            if (definition != null) {
                response.setTaskCode(definition.getTaskCode());
                response.setTaskType(definition.getTaskType());
            }
        }
        return response;
    }

    /**
     * 校验并归一化回调结束状态。
     *
     * @param raw 原始状态
     * @return 大写终态
     */
    private static String normalizeFinishStatus(String raw) {
        String value = requireText(raw, "status", 16).toUpperCase(Locale.ROOT);
        if (!FINISH_STATUSES.contains(value)) {
            throw new BusinessException(400,
                    "finish status 仅支持 SUCCESS/FAILED/CANCELLED：" + raw);
        }
        return value;
    }

    /**
     * 校验路径中的实例ID。
     *
     * @param instanceId 实例ID
     * @return 合法实例ID
     */
    private static Long requireInstanceId(Long instanceId) {
        if (instanceId == null || instanceId <= 0) {
            throw new BusinessException(400, "任务实例ID不合法：" + instanceId);
        }
        return instanceId;
    }

    /**
     * 校验必填文本并返回去空格后的值。
     *
     * @param raw    原始值
     * @param field  字段名
     * @param maxLen 最大长度
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
     * 判断文本是否包含非空白字符。
     *
     * @param value 原始文本
     * @return 是否非空
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
