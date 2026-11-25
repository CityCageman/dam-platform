package com.dam.platform.taskcenter.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.vo.PageResult;
import com.dam.platform.common.vo.Result;
import com.dam.platform.taskcenter.dto.TaskCallbackFinishRequest;
import com.dam.platform.taskcenter.dto.TaskCallbackRequest;
import com.dam.platform.taskcenter.dto.TaskCallbackResponse;
import com.dam.platform.taskcenter.dto.TaskDefinitionRequest;
import com.dam.platform.taskcenter.dto.TaskInstanceCancelRequest;
import com.dam.platform.taskcenter.dto.TaskLogRequest;
import com.dam.platform.taskcenter.dto.TaskStatusRequest;
import com.dam.platform.taskcenter.entity.TaskDefinition;
import com.dam.platform.taskcenter.entity.TaskInstance;
import com.dam.platform.taskcenter.entity.TaskLog;
import com.dam.platform.taskcenter.service.TaskCallbackService;
import com.dam.platform.taskcenter.service.TaskDefinitionService;
import com.dam.platform.taskcenter.service.TaskInstanceService;
import com.dam.platform.taskcenter.service.TaskLogService;
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
 * 提供任务定义 CRUD、手动触发、任务实例查询/状态流转、日志追加与统一执行回调。
 * 本服务只提供数据库支撑，不实现任务执行器：触发接口只创建 PENDING 实例，
 * 返回 message=executor not implemented，不伪造执行成功。
 * 回调 start 支持按来源三元组幂等登记实例，并按需自动登记任务定义；
 * 回调 finish/log/get 复用 task_instance/task_log 正常工作。
 * 另提供超时回收状态查询与手动扫描（不提供伪造 SUCCESS 的入口），
 * 以及终态实例重试（新建 PENDING 实例）与活跃实例人工取消。
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

    /** 统一任务回调服务 */
    private final TaskCallbackService taskCallbackService;

    /**
     * 任务中心运行状态。
     *
     * @return 服务名、运行状态、角色、存储 schema 与执行器状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "platform-task-center");
        status.put("status", "UP");
        status.put("role", "unified task center");
        status.put("storage", "dam_dpm");
        status.put("executor", "not implemented");
        return Result.success(status);
    }

    /**
     * 查询超时回收配置与最近一次扫描结果。
     * <p>
     * 返回 pendingTimeoutMinutes/runningTimeoutMinutes/recoverIntervalMs 等配置，
     * 以及 lastScanAt、lastRecoveredCount。只读，不触发回收。
     *
     * @return 回收配置与最近扫描结果
     */
    @GetMapping("/recovery/status")
    public Result<Map<String, Object>> recoveryStatus() {
        return Result.success(taskInstanceService.recoveryStatus());
    }

    /**
     * 手动触发一次超时回收扫描（需登录）。
     * <p>
     * 与定时任务共用同一套逻辑：PENDING 超时标记 CANCELLED，RUNNING 超时标记 FAILED，
     * 单次最多处理 100 条；不提供任何手动标记 SUCCESS 的入口。
     *
     * @return 本次扫描时间、回收总数与分类计数
     */
    @PostMapping("/recovery/run")
    public Result<Map<String, Object>> runRecovery() {
        requireLogin();
        return Result.success(taskInstanceService.recoverTimeoutInstances());
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
     * 重试终态任务实例。
     * <p>
     * 仅允许 SUCCESS/FAILED/CANCELLED 的实例重试；PENDING/RUNNING 返回 409，
     * 任务定义不存在（含已删除）返回 404。基于原 definitionId 新建一条
     * status=PENDING、triggerType=MANUAL 的实例，run_no 在定义行锁内分配，
     * 原实例保持不变；不执行任务，message 不会伪造执行成功。
     *
     * @param id 原实例ID
     * @return newInstanceId/runNo/message 等重试受理信息
     */
    @PostMapping("/instances/{id}/retry")
    public Result<Map<String, Object>> retryInstance(@PathVariable Long id) {
        return Result.success(taskInstanceService.retry(id));
    }

    /**
     * 人工取消任务实例。
     * <p>
     * 请求体可省略，或传 {"reason":"..."}；reason 为空白时使用“人工取消”。
     * PENDING/RUNNING → CANCELLED，并回填 finishedAt=now、errorMessage=reason；
     * 已 CANCELLED 幂等返回现有实例；SUCCESS/FAILED 返回 409。
     *
     * @param id      实例ID
     * @param request 取消请求（可空）
     * @return 取消后的实例
     */
    @PostMapping("/instances/{id}/cancel")
    public Result<TaskInstance> cancelInstance(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) TaskInstanceCancelRequest request) {
        return Result.success(taskInstanceService.cancel(id, request));
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

    /**
     * 回调开始：登记/复用任务实例。
     * <p>
     * 先按 definitionId 或 sourceService+sourceTaskCode 解析任务定义；
     * 未找到时默认自动登记 ENABLED 定义（autoCreateDefinition=false 则返回 404）。
     * 定义就绪后按 sourceService+sourceTaskCode+sourceRunId 幂等查重/创建
     * PENDING 实例，不写入执行结果。
     *
     * @param request 回调开始请求
     * @return 新建或复用的任务实例快照（含 definitionId/taskCode，created/reused 标识）
     */
    @PostMapping("/callbacks/start")
    public Result<TaskCallbackResponse> startCallback(
            @Valid @RequestBody TaskCallbackRequest request) {
        return Result.success(taskCallbackService.start(request));
    }

    /**
     * 回调结束：把实例更新为终态 SUCCESS/FAILED/CANCELLED。
     * <p>
     * 回填 startedAt/finishedAt；errorMessage 写入实例；
     * rowsRead/rowsWritten 因实例无对应列，以结构化日志写入 task_log 并回显。
     *
     * @param instanceId 实例ID
     * @param request    回调结束请求
     * @return 更新后的实例快照
     */
    @PostMapping("/callbacks/{instanceId}/finish")
    public Result<TaskCallbackResponse> finishCallback(
            @PathVariable Long instanceId,
            @Valid @RequestBody TaskCallbackFinishRequest request) {
        return Result.success(taskCallbackService.finish(instanceId, request));
    }

    /**
     * 回调日志：追加一条任务实例日志。
     *
     * @param instanceId 实例ID
     * @param request    日志请求（message 必填，level 缺省 INFO）
     * @return 追加后的日志
     */
    @PostMapping("/callbacks/{instanceId}/log")
    public Result<TaskLog> appendCallbackLog(
            @PathVariable Long instanceId,
            @Valid @RequestBody TaskLogRequest request) {
        return Result.success(taskCallbackService.appendLog(instanceId, request));
    }

    /**
     * 查询回调实例。
     *
     * @param instanceId 实例ID
     * @return 实例快照（含从结束日志读取的 rowsRead/rowsWritten）
     */
    @GetMapping("/callbacks/{instanceId}")
    public Result<TaskCallbackResponse> callback(@PathVariable Long instanceId) {
        return Result.success(taskCallbackService.get(instanceId));
    }

    /**
     * 校验当前请求已登录；未登录统一转换为 401 业务异常。
     * <p>
     * 仅用于手动触发回收扫描等运维操作，避免匿名请求反复触发全量扫描。
     */
    private static void requireLogin() {
        try {
            StpUtil.checkLogin();
        } catch (NotLoginException e) {
            throw new BusinessException(401, "未登录");
        }
    }
}
