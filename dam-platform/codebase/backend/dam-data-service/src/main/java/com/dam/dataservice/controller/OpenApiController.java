package com.dam.dataservice.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.Result;
import com.dam.dataservice.dto.DataApiExecuteRequest;
import com.dam.dataservice.dto.DataApiExecutionResult;
import com.dam.dataservice.dto.DataApiSchemaResult;
import com.dam.dataservice.service.OpenApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开放 API 运行入口（面向调用方）
 * <p>
 * 与 {@code /api/data-service/**} 管理接口隔离，仅提供已发布数据 API 的运行与
 * Schema 探测。访问要求：
 * <ol>
 *   <li>请求已登录（Sa-Token，未登录返回 401）；</li>
 *   <li>apiCode 对应 data_api 存在且 status=PUBLISHED（否则 404）；</li>
 *   <li>当前登录用户在 api_subscription 中存在 status=ACTIVE 的订阅（否则 403，
 *       响应不泄露订阅状态）；</li>
 *   <li>同一用户 + API 每分钟不超过 60 次（否则 429，单实例内存限流）。</li>
 * </ol>
 * 响应统一使用 {@link Result} 包装；请求参数与上游连接配置不写入日志。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/open-api/v1")
@RequiredArgsConstructor
public class OpenApiController {

    /** 开放 API 运行服务 */
    private final OpenApiService openApiService;

    /**
     * 执行已发布且已订阅的数据 API
     * <p>
     * 请求体形如 {@code {"params":{"name":"value"}}}；查询中的 {@code :name}
     * 会按出现顺序绑定为 PreparedStatement 参数，不拼接 SQL。
     *
     * @param apiCode API 编码
     * @param request 运行请求（params 可为空）
     * @return 列信息、数据行、行数与耗时
     */
    @PostMapping("/{apiCode}")
    public Result<DataApiExecutionResult> execute(
            @PathVariable String apiCode,
            @RequestBody(required = false) DataApiExecuteRequest request) {
        String loginId = requireLogin();
        Map<String, Object> params = request == null ? null : request.getParams();
        return Result.success(openApiService.execute(apiCode, loginId, params));
    }

    /**
     * 探测已发布且已订阅的数据 API 的查询结构与参数
     * <p>
     * 不返回真实数据：服务端通过 {@code LIMIT 0} 获取结果集元数据。
     *
     * @param apiCode API 编码
     * @return 参数名列表与列信息
     */
    @GetMapping("/{apiCode}/schema")
    public Result<DataApiSchemaResult> schema(@PathVariable String apiCode) {
        String loginId = requireLogin();
        return Result.success(openApiService.schema(apiCode, loginId));
    }

    /**
     * 校验当前请求已登录，并返回登录用户标识
     * <p>
     * 未登录统一转换为 401 业务异常，避免 Sa-Token 异常落到兜底 500 处理。
     *
     * @return 当前登录用户标识（订阅方）
     */
    private static String requireLogin() {
        try {
            StpUtil.checkLogin();
            return StpUtil.getLoginIdAsString();
        } catch (NotLoginException e) {
            throw new BusinessException(401, "未登录");
        }
    }
}
