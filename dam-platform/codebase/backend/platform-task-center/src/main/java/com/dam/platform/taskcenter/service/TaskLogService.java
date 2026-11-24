package com.dam.platform.taskcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.common.exception.BusinessException;
import com.dam.platform.taskcenter.dto.TaskLogRequest;
import com.dam.platform.taskcenter.entity.TaskInstance;
import com.dam.platform.taskcenter.entity.TaskLog;
import com.dam.platform.taskcenter.repository.TaskInstanceMapper;
import com.dam.platform.taskcenter.repository.TaskLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 任务日志服务。
 * <p>
 * 负责按实例查询日志与追加日志。日志只追加、不修改；追加前校验实例存在，
 * 日志级别限定为 TRACE/DEBUG/INFO/WARN/ERROR，缺省 INFO。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class TaskLogService {

    /** 合法日志级别 */
    private static final Set<String> LOG_LEVELS = Set.of(
            "TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    /** 日志内容最大长度 */
    private static final int MAX_MESSAGE_LENGTH = 10000;

    /** 任务实例 Mapper（校验实例存在） */
    private final TaskInstanceMapper taskInstanceMapper;

    /** 任务日志 Mapper */
    private final TaskLogMapper taskLogMapper;

    /**
     * 查询指定实例的全部日志（按记录时间/ID 升序）。
     *
     * @param instanceId 实例ID
     * @return 日志列表
     */
    public List<TaskLog> list(Long instanceId) {
        requireInstance(instanceId);
        return taskLogMapper.selectList(new LambdaQueryWrapper<TaskLog>()
                .eq(TaskLog::getInstanceId, instanceId)
                .orderByAsc(TaskLog::getLoggedAt)
                .orderByAsc(TaskLog::getId));
    }

    /**
     * 追加一条任务日志。
     *
     * @param instanceId 实例ID
     * @param request    日志请求（message 必填，level 缺省 INFO）
     * @return 追加后的日志
     */
    @Transactional
    public TaskLog append(Long instanceId, TaskLogRequest request) {
        requireInstance(instanceId);
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String message = request.getMessage() == null ? null : request.getMessage().trim();
        if (message == null || message.isEmpty()) {
            throw new BusinessException(400, "message 不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(400,
                    "message 长度不能超过 " + MAX_MESSAGE_LENGTH);
        }
        String level = normalizeLevel(request.getLevel());
        TaskLog log = new TaskLog();
        log.setInstanceId(instanceId);
        log.setLogLevel(level);
        log.setMessage(message);
        log.setLoggedAt(LocalDateTime.now());
        taskLogMapper.insert(log);
        return log;
    }

    /**
     * 校验实例存在，不存在时抛出 404 业务异常。
     *
     * @param instanceId 实例ID
     */
    private void requireInstance(Long instanceId) {
        if (instanceId == null) {
            throw new BusinessException(400, "任务实例ID不能为空");
        }
        TaskInstance instance = taskInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(404, "任务实例不存在：" + instanceId);
        }
    }

    /**
     * 归一化并校验日志级别；空值缺省 INFO。
     *
     * @param raw 原始级别
     * @return 大写级别
     */
    private static String normalizeLevel(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "INFO";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!LOG_LEVELS.contains(value)) {
            throw new BusinessException(400,
                    "level 不合法，仅支持 TRACE/DEBUG/INFO/WARN/ERROR：" + raw);
        }
        return value;
    }
}
