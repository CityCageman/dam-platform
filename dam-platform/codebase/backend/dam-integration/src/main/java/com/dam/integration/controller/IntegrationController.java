package com.dam.integration.controller;

import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import com.dam.integration.entity.IntegrationJob;
import com.dam.integration.entity.IntegrationRun;
import com.dam.integration.model.ConnectorDefinition;
import com.dam.integration.service.IntegrationCatalogService;
import com.dam.integration.service.IntegrationDiagnosticsService;
import com.dam.integration.service.IntegrationExecutionService;
import com.dam.integration.service.IntegrationJobService;
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
 * 数据集成接口
 * <p>
 * 提供运行状态、连接器目录、集成任务管理与运行记录查询。所有响应统一使用 {@link Result} 包装；
 * 校验失败与任务不存在分别抛出 400/404 业务异常，由 dam-common 的全局异常处理器转换。
 * <p>
 * 任务 trigger 使用异步 JDBC 全量同步执行器，接口只返回受理结果与 runId，不阻塞请求。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {

    /** 数据集成目录服务 */
    private final IntegrationCatalogService integrationCatalogService;

    /** 集成任务管理服务 */
    private final IntegrationJobService integrationJobService;

    /** 集成同步执行服务（运行记录查询） */
    private final IntegrationExecutionService integrationExecutionService;

    /** 集成诊断服务（连接测试、数据预览与运行重试） */
    private final IntegrationDiagnosticsService integrationDiagnosticsService;

    /**
     * 服务运行状态
     *
     * @return service、status 与 role
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "dam-integration");
        status.put("status", "UP");
        status.put("role", "data integration");
        return Result.success(status);
    }

    /**
     * 获取支持的连接器类型
     *
     * @return JDBC、CDC、FILE、API、KAFKA 五类连接器
     */
    @GetMapping("/connectors")
    public Result<List<Map<String, Object>>> connectors() {
        List<Map<String, Object>> connectors = integrationCatalogService.listConnectors().stream()
                .map(ConnectorDefinition::toMap)
                .toList();
        return Result.success(connectors);
    }

    /**
     * 分页查询集成任务
     *
     * @param keyword  关键字，模糊匹配任务编码或任务名称
     * @param status   任务状态精确过滤（DRAFT/ENABLED/DISABLED/RUNNING/FAILED）
     * @param page     页码，从 1 开始，默认 1
     * @param pageSize 每页条数，默认 20，最大 200
     * @return 任务分页结果
     */
    @GetMapping("/jobs")
    public Result<PageResult<IntegrationJob>> jobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(integrationJobService.pageJobs(keyword, status, page, pageSize));
    }

    /**
     * 获取集成任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @GetMapping("/jobs/{id}")
    public Result<IntegrationJob> job(@PathVariable Long id) {
        return Result.success(integrationJobService.getJob(id));
    }

    /**
     * 创建集成任务
     *
     * @param job 任务配置（jobCode/jobName/sourceType/targetType 必填）
     * @return 创建后的任务
     */
    @PostMapping("/jobs")
    public Result<IntegrationJob> create(@RequestBody IntegrationJob job) {
        return Result.success(integrationJobService.createJob(job));
    }

    /**
     * 更新集成任务
     * <p>
     * jobCode 不允许修改；请求中为 null 的字段保持原值。
     *
     * @param id  任务 ID
     * @param job 待更新字段
     * @return 更新后的任务
     */
    @PutMapping("/jobs/{id}")
    public Result<IntegrationJob> update(@PathVariable Long id, @RequestBody IntegrationJob job) {
        return Result.success(integrationJobService.updateJob(id, job));
    }

    /**
     * 逻辑删除集成任务
     *
     * @param id 任务 ID
     * @return 空结果
     */
    @DeleteMapping("/jobs/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        integrationJobService.deleteJob(id);
        return Result.success();
    }

    /**
     * 切换任务启用/停用状态
     * <p>
     * 只允许 ENABLED / DISABLED，非法状态返回 400。
     * 状态既可通过 JSON 请求体 {"status":"ENABLED"} 传入，也可通过查询参数 ?status=ENABLED 传入。
     *
     * @param id     任务 ID
     * @param status 查询参数形式的目标状态
     * @param body   JSON 请求体形式的目标状态
     * @return 更新后的任务
     */
    @PostMapping("/jobs/{id}/status")
    public Result<IntegrationJob> changeStatus(@PathVariable Long id,
                                               @RequestParam(required = false) String status,
                                               @RequestBody(required = false) Map<String, Object> body) {
        String target = status;
        if ((target == null || target.isBlank()) && body != null && body.get("status") != null) {
            target = String.valueOf(body.get("status"));
        }
        return Result.success(integrationJobService.changeStatus(id, target));
    }

    /**
     * 触发任务（异步执行）
     * <p>
     * 创建 PENDING 运行记录后立即返回 accepted=true、runId、status=ACCEPTED；
     * 同一任务已有 PENDING/RUNNING 记录时返回 409。
     *
     * @param id 任务 ID
     * @return accepted、runId、jobId、runNo、status
     */
    @PostMapping("/jobs/{id}/trigger")
    public Result<Map<String, Object>> trigger(@PathVariable Long id) {
        return Result.success(integrationJobService.triggerJob(id));
    }

    /**
     * 查询任务的运行记录（按 run_no 倒序）
     *
     * @param id    任务 ID
     * @param limit 返回条数，默认 50，最大 200
     * @return 运行记录列表
     */
    @GetMapping("/jobs/{id}/runs")
    public Result<List<IntegrationRun>> runs(@PathVariable Long id,
                                             @RequestParam(defaultValue = "50") Integer limit) {
        return Result.success(integrationExecutionService.listRuns(id, limit));
    }

    /**
     * 查询运行记录详情
     *
     * @param runId 运行记录 ID
     * @return 运行记录
     */
    @GetMapping("/runs/{runId}")
    public Result<IntegrationRun> run(@PathVariable Long runId) {
        return Result.success(integrationExecutionService.getRun(runId));
    }

    /**
     * 测试任务源端/目标端连接
     * <p>
     * 真实建立 JDBC 连接并执行 SELECT 1。任意一端失败仍返回 code=200，
     * 由 data.ok / data.source.ok / data.target.ok 标记结果；不创建运行记录，
     * 不影响任务状态与统一任务中心回调。密码不会出现在响应中。
     *
     * @param id 任务 ID
     * @return jobId、整体 ok 与 source/target 的 ok/databaseProduct/version/elapsedMs/error
     */
    @PostMapping("/jobs/{id}/test-connection")
    public Result<Map<String, Object>> testConnection(@PathVariable Long id) {
        return Result.success(integrationDiagnosticsService.testConnection(id));
    }

    /**
     * 预览任务源端数据
     * <p>
     * 仅支持 JDBC 源端，执行 source query 或 SELECT * FROM schema.table，只读源端，
     * 最多返回 100 行；不写目标端、不创建运行记录。列名包含 password/pwd/secret/token
     * 的列，其行值会替换为 ***。
     *
     * @param id    任务 ID
     * @param limit 返回行数，默认 20，超过 1~100 时收敛到边界值
     * @return columns、rows、rowCount、truncated
     */
    @PostMapping("/jobs/{id}/preview")
    public Result<Map<String, Object>> preview(@PathVariable Long id,
                                               @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(integrationDiagnosticsService.preview(id, limit));
    }

    /**
     * 重试历史运行记录
     * <p>
     * 仅允许 FAILED/SUCCESS/CANCELLED 的终态运行重试；存在 PENDING/RUNNING 活跃运行时
     * 返回 409。复用 triggerJob 创建新运行记录，返回新 runId 与原 runId。
     *
     * @param runId 原运行记录 ID
     * @return 新运行记录信息与 retriedFromRunId
     */
    @PostMapping("/runs/{runId}/retry")
    public Result<Map<String, Object>> retry(@PathVariable Long runId) {
        return Result.success(integrationDiagnosticsService.retryRun(runId));
    }
}
