package com.dam.scheduler.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import com.dam.scheduler.entity.ScheduleJob;
import com.dam.scheduler.entity.ScheduleRun;
import com.dam.scheduler.service.ScheduleDispatchService;
import com.dam.scheduler.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
 * 调度服务 API。
 * <p>
 * 提供调度任务的数据库持久化管理、启停、手动触发、Cron 自动派发扫描与运行记录查询。
 * 派发只落 PENDING 记录，不执行真实 DAG（执行引擎后续实现）。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final SchedulerService schedulerService;

    private final ScheduleDispatchService dispatchService;

    /**
     * 查询调度服务状态。
     *
     * @return service=dam-scheduler、status=UP、role=data scheduler
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(schedulerService.getStatus());
    }

    /**
     * 查询 Cron 自动派发状态。
     *
     * @return enabled、lastScanAt、lastDispatchedCount
     */
    @GetMapping("/dispatch/status")
    public Result<Map<String, Object>> dispatchStatus() {
        return Result.success(dispatchService.getStatus());
    }

    /**
     * 手动触发一次自动派发扫描（需登录）。
     * <p>
     * 该接口只扫描并派发当前 due 的任务，不补发历史周期，也不执行 DAG。
     *
     * @return 本次派发数 dispatchedCount
     */
    @PostMapping("/dispatch/run")
    public Result<Map<String, Object>> runDispatch() {
        requireLogin();
        int dispatched = dispatchService.scanOnce();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dispatchedCount", dispatched);
        return Result.success(result);
    }

    /**
     * 分页查询调度任务。
     *
     * @param keyword  任务编码/名称关键字
     * @param status   状态：DRAFT/ENABLED/DISABLED
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/jobs")
    public Result<PageResult<ScheduleJob>> jobs(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(schedulerService.pageJobs(keyword, status, page, pageSize));
    }

    /**
     * 查询调度任务详情。
     *
     * @param id 任务ID
     * @return 任务详情
     */
    @GetMapping("/jobs/{id}")
    public Result<ScheduleJob> job(@PathVariable Long id) {
        return Result.success(schedulerService.getJob(id));
    }

    /**
     * 创建调度任务。
     *
     * @param request 任务定义（jobCode/jobName/cronExpression 必填）
     * @return 创建后的任务
     */
    @PostMapping("/jobs")
    public Result<ScheduleJob> createJob(@RequestBody ScheduleJob request) {
        return Result.success(schedulerService.createJob(request));
    }

    /**
     * 更新调度任务；jobCode 不允许修改。
     *
     * @param id      任务ID
     * @param request 待更新字段
     * @return 更新后的任务
     */
    @PutMapping("/jobs/{id}")
    public Result<ScheduleJob> updateJob(@PathVariable Long id,
                                         @RequestBody ScheduleJob request) {
        return Result.success(schedulerService.updateJob(id, request));
    }

    /**
     * 逻辑删除调度任务。
     *
     * @param id 任务ID
     * @return 空结果
     */
    @DeleteMapping("/jobs/{id}")
    public Result<Void> deleteJob(@PathVariable Long id) {
        schedulerService.deleteJob(id);
        return Result.success();
    }

    /**
     * 启用/停用调度任务。
     * <p>
     * 状态支持通过 JSON 请求体 {"status":"ENABLED"} 或查询参数 ?status=ENABLED 传入。
     *
     * @param id     任务ID
     * @param status 查询参数形式的状态
     * @param body   JSON 请求体形式的状态
     * @return 更新后的任务
     */
    @PostMapping("/jobs/{id}/status")
    public Result<ScheduleJob> updateStatus(@PathVariable Long id,
                                            @RequestParam(required = false) String status,
                                            @RequestBody(required = false) Map<String, Object> body) {
        String target = status;
        if (!StringUtils.hasText(target) && body != null && body.get("status") != null) {
            target = String.valueOf(body.get("status"));
        }
        return Result.success(schedulerService.updateJobStatus(id, target));
    }

    /**
     * 触发一次调度任务。
     * <p>
     * 插入一条 run_status=PENDING 的运行记录并返回 accepted=true/runId；
     * message 固定为 execution engine not implemented，不伪造执行成功。
     *
     * @param id 任务ID
     * @return 触发受理结果
     */
    @PostMapping("/jobs/{id}/trigger")
    public Result<Map<String, Object>> trigger(@PathVariable Long id) {
        return Result.success(schedulerService.triggerJob(id));
    }

    /**
     * 查询任务运行记录（倒序）。
     *
     * @param id 任务ID
     * @return 运行记录列表
     */
    @GetMapping("/jobs/{id}/runs")
    public Result<List<ScheduleRun>> runs(@PathVariable Long id) {
        return Result.success(schedulerService.listRuns(id));
    }

    /**
     * 查询 DAG 目录。
     * <p>
     * DAG 定义与编排能力后续实现，当前固定返回空列表。
     *
     * @return 空列表
     */
    @GetMapping("/dags")
    public Result<List<Map<String, Object>>> dags() {
        return Result.success(schedulerService.listDags());
    }

    /**
     * 校验当前请求已登录；未登录统一转换为 401 业务异常。
     */
    private static void requireLogin() {
        try {
            StpUtil.checkLogin();
        } catch (NotLoginException e) {
            throw new BusinessException(401, "未登录");
        }
    }
}
