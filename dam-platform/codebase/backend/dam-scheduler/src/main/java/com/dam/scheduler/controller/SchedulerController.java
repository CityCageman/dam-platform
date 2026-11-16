package com.dam.scheduler.controller;

import com.dam.common.vo.Result;
import com.dam.scheduler.model.ScheduleJob;
import com.dam.scheduler.service.SchedulerCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度服务 API（骨架）。
 * <p>
 * 提供状态查询、任务目录、DAG 目录与任务触发占位接口；
 * 当前阶段不连接数据库，也不执行真实调度。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final SchedulerCatalogService catalogService;

    /**
     * 查询调度服务状态。
     *
     * @return service=dam-scheduler、status=UP、role=data scheduler
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(catalogService.getStatus());
    }

    /**
     * 查询调度任务目录。
     *
     * @return 静态示例任务列表，字段为 code、name、cron、status
     */
    @GetMapping("/jobs")
    public Result<List<Map<String, Object>>> jobs() {
        List<Map<String, Object>> jobs = catalogService.listJobs().stream()
                .map(SchedulerController::toJobView)
                .toList();
        return Result.success(jobs);
    }

    /**
     * 查询 DAG 目录。
     *
     * @return 当前为空列表
     */
    @GetMapping("/dags")
    public Result<List<Map<String, Object>>> dags() {
        return Result.success(catalogService.listDags());
    }

    /**
     * 触发调度任务（占位）。
     * <p>
     * 骨架阶段固定返回 accepted=false 与 message=not implemented，不伪造执行成功。
     *
     * @param id 任务标识
     * @return 触发受理结果
     */
    @PostMapping("/jobs/{id}/trigger")
    public Result<Map<String, Object>> trigger(@PathVariable String id) {
        return Result.success(catalogService.triggerJob(id));
    }

    /**
     * 将任务模型转换为接口视图，保持字段顺序稳定。
     *
     * @param job 任务模型
     * @return 包含 code、name、cron、status 的视图
     */
    private static Map<String, Object> toJobView(ScheduleJob job) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", job.getCode());
        view.put("name", job.getName());
        view.put("cron", job.getCron());
        view.put("status", job.getStatus());
        return view;
    }
}
