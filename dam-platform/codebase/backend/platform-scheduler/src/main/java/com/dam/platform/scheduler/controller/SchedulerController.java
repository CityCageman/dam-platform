package com.dam.platform.scheduler.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import com.dam.platform.scheduler.dto.ScheduleDagRequest;
import com.dam.platform.scheduler.dto.ScheduleDagView;
import com.dam.platform.scheduler.entity.ScheduleJob;
import com.dam.platform.scheduler.entity.ScheduleRun;
import com.dam.platform.scheduler.service.DolphinEngineService;
import com.dam.platform.scheduler.service.ScheduleDagService;
import com.dam.platform.scheduler.service.ScheduleDispatchService;
import com.dam.platform.scheduler.service.SchedulerService;
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
 * 提供调度任务与 DAG 的持久化管理、启停、手动触发、Cron 自动派发扫描与运行记录查询。
 * 触发/派发提交后由基础顺序 SQL DAG 执行器异步执行。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final SchedulerService schedulerService;

    private final ScheduleDispatchService dispatchService;

    private final ScheduleDagService dagService;

    private final DolphinEngineService dolphinEngineService;

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
     * 查询执行引擎状态。
     * <p>
     * 返回全局引擎类型、DolphinScheduler 开关与配置是否完整、最近一次连通性探测结果；
     * 不返回 base-url/token 等敏感配置。
     *
     * @return engine、dolphin.enabled/configured、lastProbe
     */
    @GetMapping("/engine/status")
    public Result<Map<String, Object>> engineStatus() {
        return Result.success(dolphinEngineService.getStatus());
    }

    /**
     * 测试 DolphinScheduler 连接（需登录）。
     * <p>
     * 只使用服务端配置，不接受调用方传入 URL；通过拉取流程定义列表验证
     * base-url/token/project-code 是否可用，不触发真实流程实例。
     *
     * @return ok/message/checkedAt/definitionCount
     */
    @PostMapping("/engine/dolphin/test")
    public Result<Map<String, Object>> testDolphin() {
        requireLogin();
        return Result.success(dolphinEngineService.testConnection());
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
     * 该接口只扫描并派发当前 due 的任务，不补发历史周期；派发事务提交后异步执行 DAG。
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
     * 插入一条 run_status=PENDING 的运行记录，事务提交后异步执行关联 DAG，
     * 返回 accepted=true/runId；message 为 execution submitted。
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
     * 查询 DAG 列表。
     *
     * @return DAG 列表，datasource.password 已脱敏为 ***
     */
    @GetMapping("/dags")
    public Result<List<ScheduleDagView>> dags() {
        return Result.success(dagService.listDags());
    }

    /**
     * 查询 DAG 详情。
     *
     * @param code DAG 编码
     * @return DAG 详情，datasource.password 已脱敏为 ***
     */
    @GetMapping("/dags/{code}")
    public Result<ScheduleDagView> dag(@PathVariable String code) {
        return Result.success(dagService.getDag(code));
    }

    /**
     * 创建 DAG。
     *
     * @param request DAG 定义（dagCode/dagName/definition 必填）
     * @return 创建后的 DAG，密码已脱敏
     */
    @PostMapping("/dags")
    public Result<ScheduleDagView> createDag(@RequestBody ScheduleDagRequest request) {
        return Result.success(dagService.createDag(request));
    }

    /**
     * 更新 DAG；dagCode 不允许修改。
     *
     * @param code    DAG 编码
     * @param request 待更新字段
     * @return 更新后的 DAG，密码已脱敏
     */
    @PutMapping("/dags/{code}")
    public Result<ScheduleDagView> updateDag(@PathVariable String code,
                                             @RequestBody ScheduleDagRequest request) {
        return Result.success(dagService.updateDag(code, request));
    }

    /**
     * 删除 DAG。
     *
     * @param code DAG 编码
     * @return 空结果
     */
    @DeleteMapping("/dags/{code}")
    public Result<Void> deleteDag(@PathVariable String code) {
        dagService.deleteDag(code);
        return Result.success();
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
