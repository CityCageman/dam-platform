package com.dam.service.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.platform.common.vo.PageResult;
import com.dam.platform.common.vo.Result;
import com.dam.service.api.dto.DataApiExecuteRequest;
import com.dam.service.api.dto.DataApiExecutionResult;
import com.dam.service.api.dto.DataApiRequest;
import com.dam.service.api.dto.DataApiSchemaResult;
import com.dam.service.api.entity.DataApi;
import com.dam.service.api.entity.DataApiSubscription;
import com.dam.service.api.service.DataApiRuntimeService;
import com.dam.service.api.service.DataApiService;
import com.dam.service.api.service.DataApiSubscriptionService;
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
 * 数据服务 API 管理接口
 * <p>
 * 提供数据服务运行状态、数据 API 的增删改查与发布/下线、已发布 API 的 JDBC
 * 运行入口与查询结构探测，以及 API 订阅关系的创建、查询与状态变更。本服务
 * 管理元数据并提供基础运行入口与 OpenAPI 鉴权/限流/审计，不实现完整 API 网关
 * （路由、转发等）。所有响应统一使用 {@link Result} 包装。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/data-service")
@RequiredArgsConstructor
public class DataServiceController {

    /** 数据 API 管理服务 */
    private final DataApiService dataApiService;

    /** 数据 API JDBC 运行服务 */
    private final DataApiRuntimeService dataApiRuntimeService;

    /** 数据 API 订阅管理服务 */
    private final DataApiSubscriptionService subscriptionService;

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
     * 分页查询数据 API 列表
     *
     * @param keyword  关键字（apiCode/apiName/path）
     * @param status   发布状态
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/apis")
    public Result<PageResult<DataApi>> pageApis(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(dataApiService.page(keyword, status, page, pageSize));
    }

    /**
     * 查询数据 API 详情
     *
     * @param id API ID
     * @return API 定义
     */
    @GetMapping("/apis/{id}")
    public Result<DataApi> getApi(@PathVariable Long id) {
        return Result.success(dataApiService.getById(id));
    }

    /**
     * 创建数据 API
     *
     * @param request 创建请求
     * @return 创建后的 API
     */
    @PostMapping("/apis")
    public Result<DataApi> createApi(@RequestBody DataApiRequest request) {
        return Result.success(dataApiService.create(request));
    }

    /**
     * 更新数据 API（apiCode 不可修改）
     *
     * @param id      API ID
     * @param request 更新请求
     * @return 更新后的 API
     */
    @PutMapping("/apis/{id}")
    public Result<DataApi> updateApi(@PathVariable Long id,
                                     @RequestBody DataApiRequest request) {
        return Result.success(dataApiService.update(id, request));
    }

    /**
     * 逻辑删除数据 API
     *
     * @param id API ID
     * @return 空结果
     */
    @DeleteMapping("/apis/{id}")
    public Result<Void> deleteApi(@PathVariable Long id) {
        dataApiService.delete(id);
        return Result.success();
    }

    /**
     * 发布数据 API（DRAFT/OFFLINE → PUBLISHED）
     *
     * @param id API ID
     * @return 发布后的 API
     */
    @PostMapping("/apis/{id}/publish")
    public Result<DataApi> publishApi(@PathVariable Long id) {
        return Result.success(dataApiService.publish(id));
    }

    /**
     * 下线数据 API（PUBLISHED → OFFLINE）
     *
     * @param id API ID
     * @return 下线后的 API
     */
    @PostMapping("/apis/{id}/offline")
    public Result<DataApi> offlineApi(@PathVariable Long id) {
        return Result.success(dataApiService.offline(id));
    }

    /**
     * 执行已发布的数据 API（需要登录）
     * <p>
     * 请求体形如 {@code {"params":{"name":"value"}}}；查询中的 {@code :name}
     * 会按出现顺序绑定为 PreparedStatement 参数，不拼接 SQL。
     *
     * @param id      API ID
     * @param request 运行请求（params 可为空）
     * @return 列信息、数据行、行数与耗时
     */
    @PostMapping("/apis/{id}/execute")
    public Result<DataApiExecutionResult> executeApi(
            @PathVariable Long id,
            @RequestBody(required = false) DataApiExecuteRequest request) {
        StpUtil.checkLogin();
        Map<String, Object> params = request == null ? null : request.getParams();
        return Result.success(dataApiRuntimeService.execute(id, params));
    }

    /**
     * 探测已发布数据 API 的查询参数与返回列结构（需要登录）
     * <p>
     * 不返回真实数据：服务端通过 {@code LIMIT 0} 获取结果集元数据。
     *
     * @param id API ID
     * @return 参数名列表与列信息
     */
    @GetMapping("/apis/{id}/execute/schema")
    public Result<DataApiSchemaResult> executeSchema(@PathVariable Long id) {
        StpUtil.checkLogin();
        return Result.success(dataApiRuntimeService.schema(id));
    }

    /**
     * 查询指定数据 API 的订阅列表
     *
     * @param id API ID
     * @return 订阅列表
     */
    @GetMapping("/apis/{id}/subscriptions")
    public Result<List<DataApiSubscription>> listSubscriptions(@PathVariable Long id) {
        return Result.success(subscriptionService.listByApi(id));
    }

    /**
     * 为指定数据 API 创建订阅
     *
     * @param id      API ID
     * @param request 订阅请求（subscriber 必填，status 默认 PENDING）
     * @return 创建后的订阅记录
     */
    @PostMapping("/apis/{id}/subscriptions")
    public Result<DataApiSubscription> createSubscription(
            @PathVariable Long id,
            @RequestBody DataApiSubscription request) {
        return Result.success(subscriptionService.create(id, request));
    }

    /**
     * 分页查询全部订阅（替换原静态空列表实现）
     *
     * @param apiId      API ID 过滤
     * @param subscriber 订阅方过滤（模糊匹配）
     * @param status     订阅状态过滤
     * @param page       页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 分页结果
     */
    @GetMapping("/subscriptions")
    public Result<PageResult<DataApiSubscription>> pageSubscriptions(
            @RequestParam(required = false) Long apiId,
            @RequestParam(required = false) String subscriber,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(subscriptionService.page(apiId, subscriber, status, page, pageSize));
    }

    /**
     * 更新订阅状态
     *
     * @param id   订阅 ID
     * @param body 请求体：{"status":"PENDING|ACTIVE|REJECTED|DISABLED"}
     * @return 更新后的订阅记录
     */
    @PostMapping("/subscriptions/{id}/status")
    public Result<DataApiSubscription> updateSubscriptionStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Object rawStatus = body == null ? null : body.get("status");
        String status = rawStatus == null ? null : String.valueOf(rawStatus);
        return Result.success(subscriptionService.updateStatus(id, status));
    }
}
