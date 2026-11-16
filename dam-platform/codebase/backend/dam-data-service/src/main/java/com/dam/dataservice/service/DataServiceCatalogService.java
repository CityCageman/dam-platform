package com.dam.dataservice.service;

import com.dam.dataservice.model.DataApiDefinition;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据服务目录服务（骨架）
 * <p>
 * 阶段 1 仅提供静态 API 目录与空订阅列表，不访问数据库；
 * 真实的数据 API 定义、发布记录与订阅关系留待后续阶段接入。
 *
 * @author DAM Platform
 */
@Service
public class DataServiceCatalogService {

    /**
     * 静态示例 API 目录
     * <p>
     * TODO 后续从数据服务库读取数据 API 定义，替换此静态列表。
     */
    private static final List<DataApiDefinition> STATIC_APIS = List.of(
            new DataApiDefinition(
                    "DS_CUSTOMER_PROFILE",
                    "客户主数据查询",
                    "/open-api/v1/customer-profile",
                    "GET",
                    "DRAFT"),
            new DataApiDefinition(
                    "DS_ASSET_SUMMARY",
                    "数据资产统计",
                    "/open-api/v1/asset-summary",
                    "GET",
                    "OFFLINE"));

    /**
     * 查询数据 API 目录
     *
     * @return 静态示例 API 列表；后续替换为数据库查询结果
     */
    public List<DataApiDefinition> listApis() {
        return STATIC_APIS;
    }

    /**
     * 查询 API 订阅列表
     * <p>
     * TODO 后续实现订阅关系查询（订阅方、API、授权状态、有效期等）。
     *
     * @return 阶段 1 固定返回空列表
     */
    public List<Map<String, Object>> listSubscriptions() {
        return List.of();
    }

    /**
     * 发布数据 API（骨架）
     * <p>
     * TODO 实现真实发布流程：校验 API 定义、生成版本、写入发布记录并同步网关注册。
     * 当前阶段不做任何持久化，明确返回未实现，避免调用方误判发布成功。
     *
     * @param apiId API 标识
     * @return 发布结果（accepted=false、message=not implemented）
     */
    public Map<String, Object> publish(String apiId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiId", apiId);
        result.put("accepted", false);
        result.put("message", "not implemented");
        return result;
    }
}
