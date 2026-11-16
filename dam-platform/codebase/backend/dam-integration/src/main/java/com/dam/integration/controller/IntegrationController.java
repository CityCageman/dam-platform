package com.dam.integration.controller;

import com.dam.common.vo.Result;
import com.dam.integration.model.ConnectorDefinition;
import com.dam.integration.service.IntegrationCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集成接口
 * <p>
 * 提供运行状态、连接器类型目录与集成任务查询。所有响应统一使用 {@link Result} 包装；
 * 任务不存在时抛出 404 业务异常，由 dam-common 的全局异常处理器转换为 Result。
 * <p>
 * 阶段 1 仅骨架：无数据源连接、无真实同步执行逻辑。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {

    /** 数据集成目录服务 */
    private final IntegrationCatalogService integrationCatalogService;

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
     * 获取集成任务列表
     * <p>
     * 阶段 1 恒返回空列表，说明信息通过 Result.message 返回（data 保持空数组，
     * 避免前端将说明误当作任务记录）。
     *
     * @return 空任务列表及说明
     */
    @GetMapping("/jobs")
    public Result<List<Map<String, Object>>> jobs() {
        Result<List<Map<String, Object>>> result = Result.success(integrationCatalogService.listJobs());
        result.setMessage(integrationCatalogService.jobsNote());
        return result;
    }

    /**
     * 获取集成任务详情
     *
     * @param id 任务 ID
     * @return 任务详情；阶段 1 任意 ID 均返回 code=404
     */
    @GetMapping("/jobs/{id}")
    public Result<Map<String, Object>> job(@PathVariable String id) {
        return Result.success(integrationCatalogService.requireJob(id));
    }
}
