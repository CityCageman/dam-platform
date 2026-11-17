package com.dam.dataservice.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.Result;
import com.dam.dataservice.dto.DataApiExecuteRequest;
import com.dam.dataservice.dto.DataApiExecutionResult;
import com.dam.dataservice.dto.DataApiSchemaResult;
import com.dam.dataservice.service.DataApiCallLogService;
import com.dam.dataservice.service.DataApiKeyService;
import com.dam.dataservice.service.OpenApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开放 API 运行入口（面向调用方）
 * <p>
 * 与 {@code /api/data-service/**} 管理接口隔离，仅提供已发布数据 API 的运行与
 * Schema 探测。访问要求：
 * <ol>
 *   <li>鉴权二选一：请求头 {@code X-API-Key} 命中 ACTIVE 且未过期的 API Key，
 *       或未携带 Key 时按现有平台登录（Sa-Token）校验；</li>
 *   <li>apiCode 对应 data_api 存在且 status=PUBLISHED（否则 404）；</li>
 *   <li>鉴权得到的 subscriber 在 api_subscription 中存在 status=ACTIVE 的订阅
 *       （否则 403，响应不泄露订阅状态）；</li>
 *   <li>同一 subscriber + API 每分钟不超过 60 次（否则 429，单实例内存限流），
 *       API Key 与平台登录共用同一限流桶。</li>
 * </ol>
 * 每次调用（成功/失败）都会写调用审计，审计失败不影响响应；请求参数、API Key
 * 与上游连接配置不写入日志或审计。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/open-api/v1")
@RequiredArgsConstructor
@Slf4j
public class OpenApiController {

    /** API Key 请求头 */
    private static final String API_KEY_HEADER = "X-API-Key";

    /** API Key 鉴权方式 */
    private static final String AUTH_TYPE_API_KEY = "API_KEY";

    /** 平台登录鉴权方式 */
    private static final String AUTH_TYPE_TOKEN = "TOKEN";

    /** 调用成功状态 */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** 调用失败状态 */
    private static final String STATUS_FAILED = "FAILED";

    /** 开放 API 运行服务 */
    private final OpenApiService openApiService;

    /** API Key 鉴权服务 */
    private final DataApiKeyService apiKeyService;

    /** 开放 API 调用审计服务 */
    private final DataApiCallLogService callLogService;

    /**
     * 执行已发布且已订阅的数据 API
     * <p>
     * 请求体形如 {@code {"params":{"name":"value"}}}；查询中的 {@code :name}
     * 会按出现顺序绑定为 PreparedStatement 参数，不拼接 SQL。携带
     * {@code X-API-Key} 时按 API Key 鉴权，否则沿用平台登录校验。
     *
     * @param apiCode API 编码
     * @param apiKey  可选 API Key（请求头 X-API-Key）
     * @param request 运行请求（params 可为空）
     * @return 列信息、数据行、行数与耗时
     */
    @PostMapping("/{apiCode}")
    public Result<DataApiExecutionResult> execute(
            @PathVariable String apiCode,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody(required = false) DataApiExecuteRequest request) {
        long startedAt = System.nanoTime();
        String authType = hasText(apiKey) ? AUTH_TYPE_API_KEY : AUTH_TYPE_TOKEN;
        String subscriber = null;
        Integer rowCount = null;
        String errorMessage = null;
        boolean success = false;
        try {
            subscriber = resolveSubscriber(apiKey);
            Map<String, Object> params = request == null ? null : request.getParams();
            DataApiExecutionResult result = openApiService.execute(apiCode, subscriber, params);
            rowCount = result.getRowCount();
            success = true;
            return Result.success(result);
        } catch (RuntimeException e) {
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            recordCall(apiCode, subscriber, authType,
                    success ? STATUS_SUCCESS : STATUS_FAILED,
                    durationMs, rowCount, success ? null : errorMessage);
        }
    }

    /**
     * 探测已发布且已订阅的数据 API 的查询结构与参数
     * <p>
     * 不返回真实数据：服务端通过 {@code LIMIT 0} 获取结果集元数据。携带
     * {@code X-API-Key} 时按 API Key 鉴权，否则沿用平台登录校验。
     *
     * @param apiCode API 编码
     * @param apiKey  可选 API Key（请求头 X-API-Key）
     * @return 参数名列表与列信息
     */
    @GetMapping("/{apiCode}/schema")
    public Result<DataApiSchemaResult> schema(
            @PathVariable String apiCode,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        long startedAt = System.nanoTime();
        String authType = hasText(apiKey) ? AUTH_TYPE_API_KEY : AUTH_TYPE_TOKEN;
        String subscriber = null;
        String errorMessage = null;
        boolean success = false;
        try {
            subscriber = resolveSubscriber(apiKey);
            DataApiSchemaResult result = openApiService.schema(apiCode, subscriber);
            success = true;
            return Result.success(result);
        } catch (RuntimeException e) {
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            recordCall(apiCode, subscriber, authType,
                    success ? STATUS_SUCCESS : STATUS_FAILED,
                    durationMs, null, success ? null : errorMessage);
        }
    }

    /**
     * 解析当前请求的订阅方
     * <p>
     * 携带非空 {@code X-API-Key} 时只做 API Key 鉴权，不要求平台登录；未携带
     * Key 时保持原有平台登录逻辑。无效 Key 不会回退到登录态。
     *
     * @param apiKey 请求头 X-API-Key（可空）
     * @return 订阅方
     */
    private String resolveSubscriber(String apiKey) {
        if (hasText(apiKey)) {
            return apiKeyService.authenticate(apiKey);
        }
        return requireLogin();
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

    /**
     * 写调用审计（best-effort）
     * <p>
     * 审计服务内部已使用 try/catch；这里再兜底一层，确保任何审计异常都不会
     * 覆盖 API 响应或原始业务异常。
     *
     * @param apiCode      API 编码
     * @param subscriber   订阅方
     * @param authType     鉴权方式
     * @param status       调用结果
     * @param durationMs   耗时（毫秒）
     * @param rowCount     返回行数
     * @param errorMessage 错误摘要
     */
    private void recordCall(String apiCode, String subscriber, String authType,
                            String status, long durationMs, Integer rowCount,
                            String errorMessage) {
        try {
            callLogService.record(apiCode, subscriber, authType, status,
                    durationMs, rowCount, errorMessage);
        } catch (RuntimeException e) {
            // 审计失败不得影响 API 响应；不记录可能包含敏感信息的异常消息
            log.warn("开放 API 审计写入异常，已忽略: errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    /**
     * 判断文本是否非空
     *
     * @param value 原始值
     * @return 是否包含非空白字符
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
