package com.dam.scheduler.service;

import com.dam.scheduler.model.ScheduleJob;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度目录服务（骨架）。
 * <p>
 * 当前只返回静态示例/空数据，不读写数据库、不触发真实调度执行。
 * <p>
 * TODO 后续从调度元数据表加载任务与 DAG，并接入调度引擎完成触发、暂停、重跑等操作。
 *
 * @author DAM Platform
 */
@Service
public class SchedulerCatalogService {

    /** 服务名，供状态接口返回 */
    private static final String SERVICE_NAME = "dam-scheduler";

    /** 服务角色，供状态接口返回 */
    private static final String SERVICE_ROLE = "data scheduler";

    /**
     * 静态示例任务。
     * <p>
     * 仅用于验证接口结构，状态统一为 DISABLED，避免被误认为已生效的调度计划。
     */
    private static final List<ScheduleJob> SAMPLE_JOBS = List.of(
            ScheduleJob.builder()
                    .code("demo-quality-daily-check")
                    .name("示例：数据质量日检")
                    .cron("0 0 2 * * ?")
                    .status("DISABLED")
                    .build(),
            ScheduleJob.builder()
                    .code("demo-lineage-refresh")
                    .name("示例：血缘关系刷新")
                    .cron("0 30 3 * * ?")
                    .status("DISABLED")
                    .build()
    );

    /**
     * 查询服务状态。
     *
     * @return 包含 service、status、role 的状态信息
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", SERVICE_NAME);
        status.put("status", "UP");
        status.put("role", SERVICE_ROLE);
        return status;
    }

    /**
     * 查询调度任务目录。
     *
     * @return 静态示例任务列表（当前阶段不访问数据库）
     */
    public List<ScheduleJob> listJobs() {
        return SAMPLE_JOBS;
    }

    /**
     * 查询 DAG 目录。
     *
     * @return 空列表；DAG 元数据能力后续阶段实现
     */
    public List<Map<String, Object>> listDags() {
        // TODO 接入 DAG 定义存储后返回真实数据
        return List.of();
    }

    /**
     * 触发调度任务（占位）。
     * <p>
     * 骨架阶段不执行任何调度逻辑，明确返回 accepted=false，避免调用方误判为已触发成功。
     *
     * @param jobId 任务标识
     * @return 包含 jobId、accepted=false、message=not implemented 的结果
     */
    public Map<String, Object> triggerJob(String jobId) {
        // TODO 接入调度引擎后校验任务是否存在，并提交一次性触发请求
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("accepted", false);
        result.put("message", "not implemented");
        return result;
    }
}
