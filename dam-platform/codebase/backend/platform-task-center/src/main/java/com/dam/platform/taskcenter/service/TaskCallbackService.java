package com.dam.platform.taskcenter.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.taskcenter.dto.TaskCallbackFinishRequest;
import com.dam.platform.taskcenter.dto.TaskCallbackRequest;
import com.dam.platform.taskcenter.dto.TaskCallbackResponse;
import com.dam.platform.taskcenter.dto.TaskLogRequest;
import com.dam.platform.taskcenter.entity.TaskDefinition;
import com.dam.platform.taskcenter.entity.TaskInstance;
import com.dam.platform.taskcenter.entity.TaskLog;
import com.dam.platform.taskcenter.repository.TaskDefinitionMapper;
import com.dam.platform.taskcenter.repository.TaskInstanceMapper;
import com.dam.platform.taskcenter.repository.TaskLogMapper;
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
 * <b>start 的任务定义策略：</b>
 * 先按 {@code sourceService + sourceTaskCode} 查找任务定义；找不到且
 * {@code autoCreateDefinition=true}（默认）时，自动登记状态为 ENABLED 的任务定义，
 * 编码由来源服务前缀与 sourceTaskCode 生成（超长时用 SHA-256 前 8 位截断），
 * 再继续按 source 三元组幂等查重/创建实例。显式指定 definitionId 或
 * {@code autoCreateDefinition=false} 时仍保持“定义不存在返回 404”的旧行为。
 * <p>
 * 幂等键 {@code sourceService + sourceTaskCode + sourceRunId} 由
 * {@code dam_task.task_instance} 的来源字段与唯一索引
 * {@code uk_task_instance_source_run} 持久化；start 在定义行锁前后各查重一次，
 * 锁后二次查重保证并发相同请求复用已提交实例。
 * <p>
 * 由于 task_instance 没有 rows_read/rows_written 字段，finish 上报的计数会以
 * {@value #FINISH_MARKER_PREFIX} 开头的结构化日志写入 task_log，并在响应中回显；
 * 这是真实持久化，不伪造实例列。
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

    /** 自动登记任务定义的 taskCode 最大长度（与 task_definition.task_code 一致） */
    private static final int MAX_TASK_CODE_LENGTH = 64;

    /** 自动登记 taskCode 超长时使用的 SHA-256 摘要长度 */
    private static final int TASK_CODE_HASH_LENGTH = 8;

    /** 自动登记 taskCode 的来源服务前缀映射 */
    private static final String SOURCE_PREFIX_INTEGRATION = "dam-integration";

    /** 调度来源服务前缀映射 */
    private static final String SOURCE_PREFIX_SCHEDULER = "dam-scheduler";

    /** 允许的回调结束状态 */
    private static final Set<String> FINISH_STATUSES = Set.of(
            "SUCCESS", "FAILED", "CANCELLED");

    /** 终态集合 */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCESS", "FAILED", "CANCELLED");

    /** 回调结束日志前缀（用于在 task_log 中持久化 rowsRead/rowsWritten） */
    private static final String FINISH_MARKER_PREFIX = "CALLBACK_FINISH ";

    /** 任务定义 Mapper（start 校验/查询来源定义、get/finish 补充任务类型） */
    private final TaskDefinitionMapper taskDefinitionMapper;

    /** 任务定义服务（start 自动登记定义，使用独立事务处理唯一冲突） */
    private final TaskDefinitionService taskDefinitionService;

    /** 任务实例 Mapper（finish 行锁更新、get 查询） */
    private final TaskInstanceMapper taskInstanceMapper;

    /** 任务日志 Mapper（写入/读取回调结束计数标记） */
    private final TaskLogMapper taskLogMapper;

    /** 任务日志服务（复用日志校验与追加逻辑） */
    private final TaskLogService taskLogService;

    /**
     * 回调开始：登记或复用任务实例。
     * <p>
     * 先按 definitionId 或 sourceService+sourceTaskCode 解析任务定义：
     * <ul>
     *     <li>显式 definitionId：只使用该定义，定义不存在或不匹配返回 404；</li>
     *     <li>没有 definitionId：先按来源服务与来源任务编码查找；</li>
     *     <li>找不到且 autoCreateDefinition=true（默认）：自动登记 ENABLED 定义，
     *         编码为“来源前缀_sourceTaskCode”，超长时用 SHA-256 前 8 位截断；</li>
     *     <li>找不到且 autoCreateDefinition=false：返回 404，提示先创建定义。</li>
     * </ul>
     * 定义就绪后，继续按 sourceService+sourceTaskCode+sourceRunId 做实例幂等查重；
     * 未命中则对定义行加锁并二次查重，再创建 PENDING 实例。start 只登记实例，
     * 不写入执行结果，终态仍由 finish 回填。
     *
     * @param request 回调开始请求
     * @return 新建或复用的任务实例快照（含正确的 definitionId/taskCode）
     */
    @Transactional
    public TaskCallbackResponse start(TaskCallbackRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String sourceService = requireText(request.getSourceService(), "sourceService", 64);
        String sourceTaskCode = requireText(request.getSourceTaskCode(), "sourceTaskCode", 64);
        String sourceRunId = requireText(request.getSourceRunId(), "sourceRunId", MAX_SOURCE_RUN_ID_LENGTH);
        String taskType = requireText(request.getTaskType(), "taskType", 32);
        String title = requireText(request.getTitle(), "title", 200);
        String payload = request.getPayload() == null ? null : JSONUtil.toJsonStr(request.getPayload());

        TaskDefinition definition = resolveDefinition(
                request.getDefinitionId(), sourceService, sourceTaskCode,
                taskType, title, request.isAutoCreateDefinition());

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
        // 加锁后必须二次查重：两个并发相同请求可能都在锁前查不到，
        // 锁内复查可让后到请求直接复用已提交实例，避免唯一键异常导致的 aborted transaction。
        TaskInstance lockedExisting = taskInstanceMapper.selectOne(
                new LambdaQueryWrapper<TaskInstance>()
                        .eq(TaskInstance::getSourceService, sourceService)
                        .eq(TaskInstance::getSourceTaskCode, sourceTaskCode)
                        .eq(TaskInstance::getSourceRunId, sourceRunId)
                        .last("LIMIT 1"));
        if (lockedExisting != null) {
            return toResponse(lockedExisting, null, null, false, true,
                    "相同来源运行已登记，返回已有实例");
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
            // 幂等键冲突发生在极小概率的跨定义并发场景；当前事务已 aborted，
            // 不能再查库，直接返回 409 让调用方重试（重试会命中已有实例）。
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
     * start 登记实例时会持久化 sourceService/sourceTaskCode/sourceRunId/title/payload。
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
     * 显式 definitionId 时只使用该定义并校验来源匹配；否则按
     * sourceService+sourceTaskCode 查找（兼容手工定义的 taskCode=sourceTaskCode
     * 与自动登记的生成 taskCode）。找不到且 autoCreateDefinition=true 时，
     * 通过 {@link TaskDefinitionService#autoRegister} 在独立事务中自动登记；
     * 若并发创建命中唯一索引（409），回滚后重新查询已提交定义。
     *
     * @param definitionId         可选任务定义ID
     * @param sourceService        来源服务
     * @param sourceTaskCode       来源任务编码
     * @param taskType             任务类型（自动登记时写入定义）
     * @param title                运行标题（自动登记时作为 taskName）
     * @param autoCreateDefinition 定义不存在时是否自动登记
     * @return 任务定义
     */
    private TaskDefinition resolveDefinition(Long definitionId,
                                             String sourceService,
                                             String sourceTaskCode,
                                             String taskType,
                                             String title,
                                             boolean autoCreateDefinition) {
        String generatedTaskCode = buildTaskCode(sourceService, sourceTaskCode);
        if (definitionId != null) {
            if (definitionId <= 0) {
                throw new BusinessException(400, "definitionId 不合法：" + definitionId);
            }
            TaskDefinition definition = taskDefinitionMapper.selectById(definitionId);
            if (definition == null) {
                throw new BusinessException(404,
                        "任务定义不存在：" + definitionId + "，请先创建任务定义");
            }
            if (!sourceService.equals(definition.getSourceService())
                    || !matchesSourceTask(definition.getTaskCode(),
                    sourceService, sourceTaskCode)) {
                throw new BusinessException(404,
                        "未找到匹配 sourceService=" + sourceService
                                + "、sourceTaskCode=" + sourceTaskCode
                                + " 的任务定义，请先创建任务定义");
            }
            return definition;
        }

        TaskDefinition definition = findBySourceTask(
                sourceService, sourceTaskCode, generatedTaskCode);
        if (definition != null) {
            return definition;
        }
        if (!autoCreateDefinition) {
            throw new BusinessException(404,
                    "未找到匹配 sourceService=" + sourceService
                            + "、sourceTaskCode=" + sourceTaskCode
                            + " 的任务定义，请先创建任务定义（或允许自动登记）");
        }

        try {
            TaskDefinition created = taskDefinitionService.autoRegister(
                    generatedTaskCode, title, taskType, sourceService);
            log.info("回调自动登记任务定义: taskCode={}, sourceService={}, sourceTaskCode={}",
                    created.getTaskCode(), sourceService, sourceTaskCode);
            return created;
        } catch (BusinessException e) {
            if (e.getCode() != 409) {
                throw e;
            }
            // 并发登记时内层事务已回滚，原事务可安全重查已提交定义
            TaskDefinition concurrent = findBySourceTask(
                    sourceService, sourceTaskCode, generatedTaskCode);
            if (concurrent != null) {
                log.info("并发自动登记命中已有定义: taskCode={}", concurrent.getTaskCode());
                return concurrent;
            }
            throw e;
        }
    }

    /**
     * 按来源服务与来源任务编码查找未删除的任务定义。
     * <p>
     * 先匹配历史约定 {@code taskCode = sourceTaskCode}，再匹配自动登记的生成编码，
     * 保证自动登记后后续相同回调可直接复用定义。
     *
     * @param sourceService     来源服务
     * @param sourceTaskCode    来源任务编码
     * @param generatedTaskCode 自动登记生成的任务编码
     * @return 任务定义；不存在时返回 null
     */
    private TaskDefinition findBySourceTask(String sourceService,
                                            String sourceTaskCode,
                                            String generatedTaskCode) {
        TaskDefinition definition = findBySourceAndCode(sourceService, sourceTaskCode);
        if (definition != null) {
            return definition;
        }
        if (!generatedTaskCode.equals(sourceTaskCode)) {
            definition = findBySourceAndCode(sourceService, generatedTaskCode);
        }
        return definition;
    }

    /**
     * 按来源服务与任务编码查询未删除定义。
     * <p>
     * ID 查询显式 flushCache，确保并发自动登记在独立事务提交后，能绕过当前
     * 事务 SqlSession 的本地缓存重新读取。
     *
     * @param sourceService 来源服务
     * @param taskCode      任务编码
     * @return 任务定义；不存在时返回 null
     */
    private TaskDefinition findBySourceAndCode(String sourceService, String taskCode) {
        Long definitionId = taskDefinitionMapper.selectIdBySourceAndTaskCode(
                sourceService, taskCode);
        return definitionId == null ? null : taskDefinitionMapper.selectById(definitionId);
    }

    /**
     * 判断任务定义的 taskCode 是否对应当前来源任务。
     * <p>
     * 同时接受手工定义的 {@code taskCode=sourceTaskCode} 与自动登记的生成编码。
     *
     * @param taskCode       任务定义编码
     * @param sourceService  来源服务
     * @param sourceTaskCode 来源任务编码
     * @return 是否匹配
     */
    private static boolean matchesSourceTask(String taskCode,
                                             String sourceService,
                                             String sourceTaskCode) {
        return sourceTaskCode.equals(taskCode)
                || buildTaskCode(sourceService, sourceTaskCode).equals(taskCode);
    }

    /**
     * 生成自动登记使用的稳定任务编码。
     * <p>
     * 编码格式为 {@code 来源服务前缀_sourceTaskCode}；超过 64 时保留前缀与可读部分，
     * 并以完整编码的 SHA-256 前 8 位作为后缀截断，保证长度不超过 64 且稳定可重算。
     *
     * @param sourceService  来源服务
     * @param sourceTaskCode 来源任务编码
     * @return 不超过 64 的任务编码
     */
    private static String buildTaskCode(String sourceService, String sourceTaskCode) {
        String prefix = sourcePrefix(sourceService);
        String base = prefix + "_" + sourceTaskCode;
        if (base.length() <= MAX_TASK_CODE_LENGTH) {
            return base;
        }
        String hash = DigestUtil.sha256Hex(base)
                .substring(0, TASK_CODE_HASH_LENGTH);
        int keep = MAX_TASK_CODE_LENGTH - prefix.length() - 2 - TASK_CODE_HASH_LENGTH;
        if (keep <= 0) {
            String hashed = prefix + "_" + hash;
            return hashed.length() <= MAX_TASK_CODE_LENGTH
                    ? hashed : hashed.substring(0, MAX_TASK_CODE_LENGTH);
        }
        int end = Math.min(keep, sourceTaskCode.length());
        return prefix + "_" + sourceTaskCode.substring(0, end) + "_" + hash;
    }

    /**
     * 解析来源服务前缀。
     * <p>
     * dam-integration → INT，dam-scheduler → SCH；其他取大写后的前 3 个字母/数字，
     * 不足 3 位时使用全部可用字符。
     *
     * @param sourceService 来源服务
     * @return 任务编码前缀
     */
    private static String sourcePrefix(String sourceService) {
        String lower = sourceService.toLowerCase(Locale.ROOT);
        if (SOURCE_PREFIX_INTEGRATION.equals(lower)) {
            return "INT";
        }
        if (SOURCE_PREFIX_SCHEDULER.equals(lower)) {
            return "SCH";
        }
        String upper = sourceService.toUpperCase(Locale.ROOT);
        StringBuilder prefix = new StringBuilder(3);
        for (int i = 0; i < upper.length() && prefix.length() < 3; i++) {
            char c = upper.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                prefix.append(c);
            }
        }
        return prefix.length() == 0 ? "TSK" : prefix.toString();
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
