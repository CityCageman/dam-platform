package com.dam.taskcenter.controller;

import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import com.dam.taskcenter.dto.TaskDefinitionRequest;
import com.dam.taskcenter.dto.TaskLogRequest;
import com.dam.taskcenter.dto.TaskStatusRequest;
import com.dam.taskcenter.entity.TaskDefinition;
import com.dam.taskcenter.entity.TaskInstance;
import com.dam.taskcenter.entity.TaskLog;
import com.dam.taskcenter.service.TaskDefinitionService;
import com.dam.taskcenter.service.TaskInstanceService;
import com.dam.taskcenter.service.TaskLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一任务中心 API。
 * <p>
 * 提供任务定义 CRUD、手动触发、任务实例查询/状态流转与日志追加。
 * 本服务只提供数据库支撑，不实现任务执行器：触发接口只创建 PENDING 实例，
 * 返回 message=executor not implemented，不伪造执行成功。
 * 所有响应统一使用 {@link Result} 包装。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/task-center")
@RequiredArgsConstructor
public class TaskCenterController {

    /** 任务定义服务 */
    private final TaskDefinitionService taskDefinitionService;

    /** 任务实例服务 */
    private final TaskInstanceService taskInstanceService;

    /** 任务日志服务 */
    private final TaskLogService taskLogService;

    /**
     * 任务中心运行状态。
     *
     * @return 服务名、运行状态、角色、存储 schema 与执行器状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "dam-task-center");
        status.put("status", "UP");
        status.put("role", "unified task center");
        status.put("storage", "dam_task");
        status.put("executor", "not implemented");
        return Result.success(status);
    }

    /**
     * 分页查询任务定义。
     *
     * @param keyword  关键字（taskCode/taskName/sourceService）
     * @param taskType 任务类型：INTEGRATION/SCHEDULER/DEV/QUALITY/AI
     * @param status   状态：DRAFT/ENABLED/DISABLED
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数（1~100）
     * @return 分页结果
     */
    @GetMapping("/definitions")
    public Result<PageResult<TaskDefinition>> definitions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(
                taskDefinitionService.page(keyword, taskType, status, page, pageSize));
    }

    /**
     * 查询任务定义详情。
     *
     * @param id 任务定义ID
     * @return 任务定义
     */
    @GetMapping("/definitions/{id}")
    public Result<TaskDefinition> definition(@PathVariable Long id) {
        return Result.success(taskDefinitionService.getById(id));
    }

    /**
     * 创建任务定义。
     * <p>
     * taskCode/taskName/taskType/sourceService 必填；taskCode 全局唯一。
     *
     * @param request 创建请求
     * @return 创建后的任务定义
     */
    @PostMapping("/definitions")
    public Result<TaskDefinition> createDefinition(
            @Valid @RequestBody TaskDefinitionRequest request) {
        return Result.success(taskDefinitionService.create(request));
    }

    /**
     * 更新任务定义；taskCode 不允许修改。
     *
     * @param id      任务定义ID
     * @param request 更新请求（未传字段保持原值）
     * @return 更新后的任务定义
     */
    @PutMapping("/definitions/{id}")
    public Result<TaskDefinition> updateDefinition(
            @PathVariable Long id,
            @RequestBody TaskDefinitionRequest request) {
        return Result.success(taskDefinitionService.update(id, request));
    }

    /**
     * 逻辑删除任务定义；历史实例与日志保留。
     *
     * @param id 任务定义ID
     * @return 空结果
     */
    @DeleteMapping("/definitions/{id}")
    public Result<Void> deleteDefinition(@PathVariable Long id) {
        taskDefinitionService.delete(id);
        return Result.success();
    }

    /**
     * 手动触发任务。
     * <p>
     * 只创建一条 PENDING 实例并返回 instanceId；不执行任务，
     * message 固定为 executor not implemented。
     *
     * @param id 任务定义ID
     * @return 触发受理信息
     */
    @PostMapping("/definitions/{id}/trigger")
    public Result<Map<String, Object>> trigger(@PathVariable Long id) {
        return Result.success(taskInstanceService.trigger(id));
    }

    /**
     * 分页查询任务实例。
     *
     * @param definitionId 任务定义ID过滤
     * @param status       实例状态过滤
     * @param page         页码（从 1 开始）
     * @param pageSize     每页条数（1~100）
     * @return 分页结果
     */
    @GetMapping("/instances")
    public Result<PageResult<TaskInstance>> instances(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(
                taskInstanceService.page(definitionId, status, page, pageSize));
    }

    /**
     * 查询任务实例详情。
     *
     * @param id 实例ID
     * @return 任务实例
     */
    @GetMapping("/instances/{id}")
    public Result<TaskInstance> instance(@PathVariable Long id) {
        return Result.success(taskInstanceService.getById(id));
    }

    /**
     * 更新任务实例状态。
     * <p>
     * 允许 PENDING→RUNNING/SUCCESS/FAILED/CANCELLED、RUNNING→SUCCESS/FAILED/CANCELLED；
     * 终态不可再流转。非法目标状态或非法流转返回 400。
     *
     * @param id      实例ID
     * @param request 状态请求
     * @return 更新后的实例
     */
    @PostMapping("/instances/{id}/status")
    public Result<TaskInstance> updateInstanceStatus(
            @PathVariable Long id,
            @Valid @RequestBody TaskStatusRequest request) {
        return Result.success(taskInstanceService.updateStatus(id, request));
    }

    /**
     * 查询指定实例的日志（按时间升序）。
     *
     * @param id 实例ID
     * @return 日志列表
     */
    @GetMapping("/instances/{id}/logs")
    public Result<List<TaskLog>> logs(@PathVariable Long id) {
        return Result.success(taskLogService.list(id));
    }

    /**
     * 追加一条任务实例日志。
     *
     * @param id      实例ID
     * @param request 日志请求（message 必填，level 缺省 INFO）
     * @return 追加后的日志
     */
    @PostMapping("/instances/{id}/logs")
    public Result<TaskLog> appendLog(@PathVariable Long id,
                                     @Valid @RequestBody TaskLogRequest request) {
        return Result.success(taskLogService.append(id, request));
    }
}
