package com.dam.dataservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.common.exception.BusinessException;
import com.dam.dataservice.dto.DataApiExecutionResult;
import com.dam.dataservice.dto.DataApiSchemaResult;
import com.dam.dataservice.entity.DataApi;
import com.dam.dataservice.entity.DataApiSubscription;
import com.dam.dataservice.ratelimit.OpenApiRateLimiter;
import com.dam.dataservice.repository.DataApiMapper;
import com.dam.dataservice.repository.DataApiSubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 开放 API 运行服务（面向调用方）
 * <p>
 * 与 {@code /api/data-service/**} 管理接口分离，只暴露「已发布 + 已订阅」的数据
 * API 运行能力。每次调用按以下顺序校验：
 * <ol>
 *   <li>按 apiCode 查询未逻辑删除的 data_api；不存在或非 PUBLISHED 统一返回 404，
 *       不暴露 API 是否曾发布；</li>
 *   <li>校验 api_subscription 存在 api_id + subscriber（当前登录用户）+
 *       status=ACTIVE 的记录；不满足统一返回 403，且响应不包含订阅状态等细节；</li>
 *   <li>按「登录用户 + apiCode」执行内存限流（60 次/分钟），超限返回 429；</li>
 *   <li>委托 {@link DataApiRuntimeService} 执行 JDBC 查询或探测 Schema。</li>
 * </ol>
 * 本服务不记录请求参数、upstreamConfig（连接 URL/用户名/密码）等敏感信息；
 * 运行时 SQL 异常由 {@link DataApiRuntimeService} 脱敏后再返回。
 * <p>
 * 限流为单实例内存实现，多实例部署时各实例独立计数，不保证全局配额。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiService {

    /** 已发布状态 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 订阅生效状态 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** apiCode 最大长度（与表结构 varchar(64) 对齐） */
    private static final int MAX_API_CODE_LENGTH = 64;

    /** 日志字段最大长度（避免超长输出） */
    private static final int MAX_LOG_FIELD_LENGTH = 100;

    /** 数据 API Mapper */
    private final DataApiMapper dataApiMapper;

    /** 订阅 Mapper */
    private final DataApiSubscriptionMapper subscriptionMapper;

    /** 数据 API JDBC 运行服务 */
    private final DataApiRuntimeService dataApiRuntimeService;

    /** 开放 API 限流器 */
    private final OpenApiRateLimiter rateLimiter;

    /**
     * 以当前登录用户身份执行已发布且已订阅的数据 API
     *
     * @param apiCode API 编码
     * @param loginId 当前登录用户标识（订阅方）
     * @param params  命名参数，可为空
     * @return 列信息、数据行、行数与耗时
     */
    public DataApiExecutionResult execute(String apiCode, String loginId,
                                          Map<String, Object> params) {
        DataApi api = authorize(apiCode, loginId);
        rateLimiter.check(loginId, api.getApiCode());
        try {
            DataApiExecutionResult result = dataApiRuntimeService.execute(api.getId(), params);
            log.info("开放 API 调用成功: apiCode={}, loginId={}, rowCount={}, elapsedMs={}",
                    safeForLog(api.getApiCode()), safeForLog(loginId),
                    result.getRowCount(), result.getElapsedMs());
            return result;
        } catch (BusinessException e) {
            throw translateAndLog(api, loginId, e);
        }
    }

    /**
     * 以当前登录用户身份探测已发布且已订阅的数据 API 的 Schema
     * <p>
     * 不返回真实数据行；仍执行登录用户订阅校验与同一限流窗口。
     *
     * @param apiCode API 编码
     * @param loginId 当前登录用户标识（订阅方）
     * @return 参数名列表与列信息
     */
    public DataApiSchemaResult schema(String apiCode, String loginId) {
        DataApi api = authorize(apiCode, loginId);
        rateLimiter.check(loginId, api.getApiCode());
        try {
            return dataApiRuntimeService.schema(api.getId());
        } catch (BusinessException e) {
            throw translateAndLog(api, loginId, e);
        }
    }

    /**
     * 校验 API 已发布且当前用户存在 ACTIVE 订阅
     *
     * @param apiCode API 编码
     * @param loginId 当前登录用户标识
     * @return 通过校验的数据 API
     */
    private DataApi authorize(String apiCode, String loginId) {
        String code = trim(apiCode);
        if (code == null || code.isEmpty() || code.length() > MAX_API_CODE_LENGTH) {
            throw new BusinessException(404, "数据 API 不存在");
        }
        DataApi api = dataApiMapper.selectByApiCode(code);
        // 非 PUBLISHED 与不存在统一返回 404，避免向调用方泄露发布状态
        if (api == null || !STATUS_PUBLISHED.equalsIgnoreCase(trim(api.getStatus()))) {
            log.debug("开放 API 不存在或未发布: apiCode={}", safeForLog(code));
            throw new BusinessException(404, "数据 API 不存在");
        }
        if (loginId == null || loginId.isBlank()) {
            // 正常流程已在 Controller 校验登录，这里兜底避免把空订阅方当作有效订阅
            throw new BusinessException(401, "未登录");
        }
        Long activeSubscriptions = subscriptionMapper.selectCount(
                new LambdaQueryWrapper<DataApiSubscription>()
                        .eq(DataApiSubscription::getApiId, api.getId())
                        .eq(DataApiSubscription::getSubscriber, loginId)
                        .eq(DataApiSubscription::getStatus, STATUS_ACTIVE));
        if (activeSubscriptions == null || activeSubscriptions <= 0) {
            // 统一提示，不区分「未订阅 / 待审批 / 已拒绝 / 已禁用」，避免泄露订阅信息
            log.warn("开放 API 订阅校验未通过: apiCode={}, loginId={}",
                    safeForLog(api.getApiCode()), safeForLog(loginId));
            throw new BusinessException(403, "无权访问该数据 API");
        }
        return api;
    }

    /**
     * 记录运行时失败日志并转换会暴露发布状态的并发异常
     * <p>
     * 运行时若在鉴权后、执行前发现 API 被下线，会抛出 409；对开放 API 调用方
     * 统一转换为 404，保持「未发布即不存在」的外部语义。其余业务异常原样抛出，
     * 其消息已由运行时完成密码/连接串脱敏。
     *
     * @param api     数据 API
     * @param loginId 当前登录用户
     * @param e       运行时业务异常
     * @return 需要继续抛出的业务异常
     */
    private BusinessException translateAndLog(DataApi api, String loginId, BusinessException e) {
        if (e.getCode() == 409) {
            log.info("开放 API 在执行前被下线: apiCode={}", safeForLog(api.getApiCode()));
            return new BusinessException(404, "数据 API 不存在");
        }
        log.warn("开放 API 调用失败: apiCode={}, loginId={}, code={}, message={}",
                safeForLog(api.getApiCode()), safeForLog(loginId),
                e.getCode(), e.getMessage());
        return e;
    }

    /**
     * 去除首尾空白
     *
     * @param value 原始值
     * @return 去空白后的值；原值为 null 时返回 null
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 日志字段脱敏：去除换行/制表符并限制长度，避免日志注入与超长输出
     *
     * @param value 原始值
     * @return 单行、限长后的文本
     */
    private String safeForLog(String value) {
        if (value == null) {
            return null;
        }
        String singleLine = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return singleLine.length() > MAX_LOG_FIELD_LENGTH
                ? singleLine.substring(0, MAX_LOG_FIELD_LENGTH) : singleLine;
    }
}
