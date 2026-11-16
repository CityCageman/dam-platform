package com.dam.develop.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.develop.dto.DevTaskCreateRequest;
import com.dam.develop.dto.DevTaskUpdateRequest;
import com.dam.develop.entity.DevTask;
import com.dam.develop.entity.DevTaskVersion;
import com.dam.develop.repository.DevTaskMapper;
import com.dam.develop.repository.DevTaskVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 数据开发任务服务
 * <p>
 * 负责任务定义的分页查询、详情、创建、更新、逻辑删除与发布状态流转，并维护
 * {@code dev_task_version} 版本快照。状态机：DRAFT/OFFLINE → PUBLISHED（发布），
 * PUBLISHED → OFFLINE（下线）。内容变更时在同一事务内递增当前版本并写入版本记录。
 * 本服务只管理任务定义，不实现真实执行引擎。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevTaskService {

    /** 默认页码 */
    private static final int DEFAULT_PAGE = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页最大条数，避免一次拉取过多数据 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 允许的任务类型 */
    private static final Set<String> TASK_TYPES = Set.of(
            DevTask.TYPE_SQL, DevTask.TYPE_PYTHON, DevTask.TYPE_SHELL);

    /** 允许的任务状态 */
    private static final Set<String> TASK_STATUSES = Set.of(
            DevTask.STATUS_DRAFT, DevTask.STATUS_PUBLISHED, DevTask.STATUS_OFFLINE);

    /** 初始版本默认变更说明 */
    private static final String DEFAULT_INITIAL_CHANGE_NOTE = "初始版本";

    /** 内容变更默认说明 */
    private static final String DEFAULT_CONTENT_CHANGE_NOTE = "内容变更";

    /** 任务 Mapper */
    private final DevTaskMapper taskMapper;

    /** 任务版本 Mapper */
    private final DevTaskVersionMapper versionMapper;

    /**
     * 分页查询任务列表
     *
     * @param keyword  关键字，模糊匹配任务编码或任务名称，可为空
     * @param taskType 任务类型精确过滤（SQL/PYTHON/SHELL），可为空
     * @param status   发布状态精确过滤（DRAFT/PUBLISHED/OFFLINE），可为空
     * @param page     页码，从 1 开始，非法值按 1 处理
     * @param pageSize 每页条数，非法值按 20 处理，最大 200
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<DevTask> page(String keyword, String taskType, String status,
                                    Integer page, Integer pageSize) {
        long current = page == null || page < 1 ? DEFAULT_PAGE : page;
        long size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<DevTask> wrapper = new LambdaQueryWrapper<>();
        String keywordValue = trimToNull(keyword);
        if (keywordValue != null) {
            // 关键字条件用 and 包裹，避免 or 扩散到类型/状态过滤条件
            String matched = keywordValue.length() > 100
                    ? keywordValue.substring(0, 100) : keywordValue;
            wrapper.and(w -> w.like(DevTask::getTaskCode, matched)
                    .or().like(DevTask::getTaskName, matched));
        }
        String typeValue = normalizeTaskTypeFilter(taskType);
        if (typeValue != null) {
            wrapper.eq(DevTask::getTaskType, typeValue);
        }
        String statusValue = normalizeStatusFilter(status);
        if (statusValue != null) {
            wrapper.eq(DevTask::getStatus, statusValue);
        }
        // 最近创建的任务排在前面；id 作为创建时间相同时的稳定排序
        wrapper.orderByDesc(DevTask::getCreatedAt).orderByDesc(DevTask::getId);
        return PageResult.of(taskMapper.selectPage(new Page<>(current, size), wrapper));
    }

    /**
     * 按 ID 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws BusinessException 任务不存在时返回 404
     */
    @Transactional(readOnly = true)
    public DevTask getTask(Long id) {
        return requireTask(id);
    }

    /**
     * 创建任务
     * <p>
     * 校验 taskCode/taskName/taskType/content；taskCode 全局唯一（包含已逻辑删除记录，
     * 与数据库唯一约束保持一致）。创建后的任务固定为 DRAFT、currentVersion=1，
     * 并写入版本 1 的初始快照。
     *
     * @param request 创建请求
     * @return 创建后的任务（含自增 ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public DevTask create(DevTaskCreateRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String taskCode = requireText(request.getTaskCode(), "taskCode", 64);
        String taskName = requireText(request.getTaskName(), "taskName", 128);
        String taskType = normalizeTaskType(request.getTaskType());
        String content = requireContent(request.getContent());

        // task_code 唯一性校验：包含已逻辑删除的记录（数据库唯一约束不区分删除状态）
        if (taskMapper.countByTaskCodeIncludingDeleted(taskCode) > 0) {
            throw new BusinessException(409, "taskCode 已存在：" + taskCode);
        }

        LocalDateTime now = LocalDateTime.now();
        String operator = currentUser();
        DevTask task = new DevTask();
        task.setTaskCode(taskCode);
        task.setTaskName(taskName);
        task.setTaskType(taskType);
        task.setDatasourceId(request.getDatasourceId());
        task.setContent(content);
        task.setStatus(DevTask.STATUS_DRAFT);
        task.setCurrentVersion(1);
        task.setDescription(trimToNull(request.getDescription()));
        task.setCreatedAt(now);
        task.setCreatedBy(operator);
        task.setUpdatedAt(now);
        task.setUpdatedBy(operator);
        // 明确忽略客户端可能伪造的 id / 删除标记
        task.setId(null);
        task.setDeletedAt(null);

        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            // 并发创建时由数据库唯一约束兜底，转换为可读的业务异常
            throw new BusinessException(409, "taskCode 已存在：" + taskCode);
        }

        // 初始版本快照与主表写入处于同一事务：任一失败整体回滚
        versionMapper.insert(buildVersion(task, 1, content,
                request.getChangeNote(), DEFAULT_INITIAL_CHANGE_NOTE, now, operator));
        log.info("数据开发任务创建成功: id={}, taskCode={}, type={}",
                task.getId(), task.getTaskCode(), task.getTaskType());
        return task;
    }

    /**
     * 更新任务
     * <p>
     * taskCode 一经创建不允许修改；请求中为 null 的字段保持原值。content 与当前内容
     * 不同时，currentVersion 加 1 并追加版本记录；content 相同则不产生新版本。
     * 版本写入与主表更新在同一事务内完成。
     *
     * @param id      任务 ID
     * @param request 更新请求
     * @return 更新后的任务
     */
    @Transactional(rollbackFor = Exception.class)
    public DevTask update(Long id, DevTaskUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        // 行锁保证并发内容更新时版本号串行递增
        DevTask task = requireTaskForUpdate(id);

        String submittedCode = trimToNull(request.getTaskCode());
        if (submittedCode != null && !submittedCode.equals(task.getTaskCode())) {
            throw new BusinessException(400, "不允许修改 taskCode");
        }
        if (request.getTaskName() != null) {
            task.setTaskName(requireText(request.getTaskName(), "taskName", 128));
        }
        if (request.getTaskType() != null) {
            task.setTaskType(normalizeTaskType(request.getTaskType()));
        }
        if (request.getDatasourceId() != null) {
            task.setDatasourceId(request.getDatasourceId());
        }
        if (request.getDescription() != null) {
            task.setDescription(trimToNull(request.getDescription()));
        }

        boolean contentChanged = false;
        String newContent = null;
        int nextVersion = task.getCurrentVersion() == null ? 1 : task.getCurrentVersion();
        if (request.getContent() != null) {
            newContent = requireContent(request.getContent());
            if (!newContent.equals(task.getContent())) {
                contentChanged = true;
                nextVersion = nextVersion + 1;
                task.setContent(newContent);
                task.setCurrentVersion(nextVersion);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String operator = currentUser();
        task.setUpdatedAt(now);
        task.setUpdatedBy(operator);
        if (taskMapper.updateById(task) == 0) {
            throw new BusinessException(404, "数据开发任务不存在: " + id);
        }

        if (contentChanged) {
            versionMapper.insert(buildVersion(task, nextVersion, newContent,
                    request.getChangeNote(), DEFAULT_CONTENT_CHANGE_NOTE, now, operator));
            log.info("数据开发任务内容更新: id={}, taskCode={}, version={}",
                    task.getId(), task.getTaskCode(), nextVersion);
        }
        return task;
    }

    /**
     * 逻辑删除任务（@TableLogic 只写 deleted_at，不物理删除任务与历史版本）
     *
     * @param id 任务 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DevTask task = requireTaskForUpdate(id);
        if (taskMapper.deleteById(task.getId()) == 0) {
            throw new BusinessException(404, "数据开发任务不存在: " + id);
        }
        log.info("数据开发任务已逻辑删除: id={}, taskCode={}", task.getId(), task.getTaskCode());
    }

    /**
     * 发布任务（DRAFT/OFFLINE → PUBLISHED）
     *
     * @param id 任务 ID
     * @return 发布后的任务
     */
    @Transactional(rollbackFor = Exception.class)
    public DevTask publish(Long id) {
        DevTask task = requireTaskForUpdate(id);
        if (DevTask.STATUS_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "数据开发任务已发布，不能重复发布");
        }
        if (!DevTask.STATUS_DRAFT.equals(task.getStatus())
                && !DevTask.STATUS_OFFLINE.equals(task.getStatus())) {
            throw new BusinessException(409, "仅 DRAFT/OFFLINE 状态的任务可以发布");
        }
        task.setStatus(DevTask.STATUS_PUBLISHED);
        touch(task);
        taskMapper.updateById(task);
        log.info("数据开发任务发布: id={}, taskCode={}", task.getId(), task.getTaskCode());
        return task;
    }

    /**
     * 下线任务（PUBLISHED → OFFLINE）
     *
     * @param id 任务 ID
     * @return 下线后的任务
     */
    @Transactional(rollbackFor = Exception.class)
    public DevTask offline(Long id) {
        DevTask task = requireTaskForUpdate(id);
        if (!DevTask.STATUS_PUBLISHED.equals(task.getStatus())) {
            throw new BusinessException(409, "仅 PUBLISHED 状态的任务可以下线");
        }
        task.setStatus(DevTask.STATUS_OFFLINE);
        touch(task);
        taskMapper.updateById(task);
        log.info("数据开发任务下线: id={}, taskCode={}", task.getId(), task.getTaskCode());
        return task;
    }

    /**
     * 查询任务的版本列表（按版本号倒序，最新版本在前）
     *
     * @param id 任务 ID
     * @return 版本列表；任务没有历史版本时返回空列表
     */
    @Transactional(readOnly = true)
    public List<DevTaskVersion> listVersions(Long id) {
        requireTask(id);
        return versionMapper.selectList(new LambdaQueryWrapper<DevTaskVersion>()
                .eq(DevTaskVersion::getTaskId, id)
                .orderByDesc(DevTaskVersion::getVersionNo));
    }

    /**
     * 查询任务指定版本详情
     *
     * @param id        任务 ID
     * @param versionNo 版本号
     * @return 版本快照
     * @throws BusinessException 任务或版本不存在时返回 404
     */
    @Transactional(readOnly = true)
    public DevTaskVersion getVersion(Long id, Integer versionNo) {
        requireTask(id);
        if (versionNo == null || versionNo < 1) {
            throw new BusinessException(400, "versionNo 必须为正整数");
        }
        DevTaskVersion version = versionMapper.selectOne(new LambdaQueryWrapper<DevTaskVersion>()
                .eq(DevTaskVersion::getTaskId, id)
                .eq(DevTaskVersion::getVersionNo, versionNo));
        if (version == null) {
            throw new BusinessException(404, "任务版本不存在: " + versionNo);
        }
        return version;
    }

    /**
     * 按 ID 查询未删除的任务，不存在时抛出业务异常
     *
     * @param id 任务 ID
     * @return 任务定义
     */
    private DevTask requireTask(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, "任务ID不合法");
        }
        DevTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "数据开发任务不存在: " + id);
        }
        return task;
    }

    /**
     * 按 ID 查询未删除任务并加行锁，不存在时抛出业务异常
     *
     * @param id 任务 ID
     * @return 任务定义
     */
    private DevTask requireTaskForUpdate(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, "任务ID不合法");
        }
        DevTask task = taskMapper.selectByIdForUpdate(id);
        if (task == null) {
            throw new BusinessException(404, "数据开发任务不存在: " + id);
        }
        return task;
    }

    /**
     * 构造版本快照
     *
     * @param task           所属任务
     * @param versionNo      版本号
     * @param content        该版本内容
     * @param requestedNote  请求中的变更说明
     * @param defaultNote    未提供变更说明时的默认值
     * @param now            创建时间
     * @param operator       创建人
     * @return 待插入的版本记录
     */
    private static DevTaskVersion buildVersion(DevTask task, int versionNo, String content,
                                               String requestedNote, String defaultNote,
                                               LocalDateTime now, String operator) {
        DevTaskVersion version = new DevTaskVersion();
        version.setTaskId(task.getId());
        version.setVersionNo(versionNo);
        version.setContent(content);
        String changeNote = optionalText(requestedNote, "changeNote", 500);
        version.setChangeNote(changeNote == null ? defaultNote : changeNote);
        version.setCreatedAt(now);
        version.setCreatedBy(operator);
        return version;
    }

    /**
     * 更新任务审计字段
     *
     * @param task 任务
     */
    private static void touch(DevTask task) {
        task.setUpdatedAt(LocalDateTime.now());
        task.setUpdatedBy(currentUser());
    }

    /**
     * 校验必填文本字段：去空格后不能为空，且不能超过指定长度
     *
     * @param value     原始值
     * @param fieldName 字段名（用于错误提示）
     * @param maxLength 最大长度
     * @return 去除首尾空格后的值
     */
    private static String requireText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(400, fieldName + " 不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException(400, fieldName + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    /**
     * 校验脚本内容：不能为空；内容本身按原样保存（保留缩进与换行）
     *
     * @param content 脚本内容
     * @return 原始脚本内容
     */
    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "content 不能为空");
        }
        return content;
    }

    /**
     * 规范化并校验必填任务类型
     *
     * @param taskType 原始类型
     * @return 大写任务类型
     */
    private static String normalizeTaskType(String taskType) {
        String normalized = trimToNull(taskType);
        if (normalized == null) {
            throw new BusinessException(400, "taskType 不能为空");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!TASK_TYPES.contains(normalized)) {
            throw new BusinessException(400, "taskType 不合法，仅支持 SQL/PYTHON/SHELL");
        }
        return normalized;
    }

    /**
     * 规范化可选的任务类型过滤条件
     *
     * @param taskType 原始类型
     * @return 大写类型；未提供时返回 null
     */
    private static String normalizeTaskTypeFilter(String taskType) {
        String normalized = trimToNull(taskType);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!TASK_TYPES.contains(normalized)) {
            throw new BusinessException(400, "taskType 不合法，仅支持 SQL/PYTHON/SHELL");
        }
        return normalized;
    }

    /**
     * 规范化可选的状态过滤条件
     *
     * @param status 原始状态
     * @return 大写状态；未提供时返回 null
     */
    private static String normalizeStatusFilter(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!TASK_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "status 不合法，仅支持 DRAFT/PUBLISHED/OFFLINE");
        }
        return normalized;
    }

    /**
     * 校验可选文本字段：为空返回 null，否则去空格并检查长度
     *
     * @param value     原始值
     * @param fieldName 字段名
     * @param maxLength 最大长度
     * @return 规范化后的值或 null
     */
    private static String optionalText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new BusinessException(400, fieldName + " 长度不能超过 " + maxLength);
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
