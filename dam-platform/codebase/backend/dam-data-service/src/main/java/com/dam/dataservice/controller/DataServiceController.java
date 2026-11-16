package com.dam.dataservice.controller;

import com.dam.common.vo.Result;
import com.dam.dataservice.model.DataApiDefinition;
import com.dam.dataservice.service.DataServiceCatalogService;
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
 * 数据服务 API 管理接口
 * <p>
 * 提供数据服务运行状态、数据 API 目录、订阅列表与 API 发布入口。
 * 阶段 1 为骨架实现：不连接数据库，发布接口明确返回未实现。
 * 所有响应统一使用 {@link Result} 包装。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/data-service")
@RequiredArgsConstructor
public class DataServiceController {

    /** 数据服务目录服务 */
    private final DataServiceCatalogService dataServiceCatalogService;

    /**
     * 数据服务运行状态
     *
     * @return 服务名、运行状态与角色
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "dam-data-service");
        status.put("status", "UP");
        status.put("role", "data service");
        return Result.success(status);
    }

    /**
     * 查询数据 API 目录
     *
     * @return 数据 API 列表，字段为 code、name、path、method、status
     */
    @GetMapping("/apis")
    public Result<List<Map<String, Object>>> apis() {
        List<Map<String, Object>> apis = dataServiceCatalogService.listApis().stream()
                .map(DataApiDefinition::toMap)
                .toList();
        return Result.success(apis);
    }

    /**
     * 查询数据 API 订阅列表
     *
     * @return 订阅列表；阶段 1 固定为空列表
     */
    @GetMapping("/subscriptions")
    public Result<List<Map<String, Object>>> subscriptions() {
        return Result.success(dataServiceCatalogService.listSubscriptions());
    }

    /**
     * 发布数据 API（骨架）
     * <p>
     * 阶段 1 不实现发布逻辑，返回 accepted=false、message=not implemented。
     *
     * @param id API 标识
     * @return 发布结果
     */
    @PostMapping("/apis/{id}/publish")
    public Result<Map<String, Object>> publish(@PathVariable String id) {
        return Result.success(dataServiceCatalogService.publish(id));
    }
}
