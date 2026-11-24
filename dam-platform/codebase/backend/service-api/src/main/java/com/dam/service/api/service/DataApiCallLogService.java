package com.dam.service.api.service;

import com.dam.service.api.entity.DataApi;
import com.dam.service.api.entity.DataApiCallLog;
import com.dam.service.api.repository.DataApiCallLogMapper;
import com.dam.service.api.repository.DataApiMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 开放 API 调用审计服务
 * <p>
 * 每次开放 API 调用（成功/失败）写入一条 {@code data_api_call_log}。审计采用
 * <b>同步、独立写入</b>：不参与调用方事务，内部使用 try/catch 吞掉所有异常，
 * 因此审计失败不会改变 API 响应或掩盖原始业务异常。
 * <p>
 * 仅记录 API 编码、订阅方、鉴权方式、结果、耗时、行数与脱敏后的错误摘要；
 * <b>不记录 API Key、密码与请求参数</b>。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataApiCallLogService {

    /** 无法解析到数据 API 时使用的 api_id 哨兵值（DDL 要求 NOT NULL） */
    private static final long UNRESOLVED_API_ID = 0L;

    /** api_code 最大长度 */
    private static final int MAX_API_CODE_LENGTH = 64;

    /** subscriber 最大长度 */
    private static final int MAX_SUBSCRIBER_LENGTH = 100;

    /** error_message 最大长度 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    /** 密码赋值片段脱敏 */
    private static final Pattern PASSWORD_ASSIGNMENT = Pattern.compile(
            "(?i)password\\s*=\\s*[^\\s;&]+");

    /** 疑似明文 API Key 脱敏 */
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "dam_[A-Za-z0-9_-]{8,}");

    /** Bearer Token 脱敏 */
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");

    /** 审计日志 Mapper */
    private final DataApiCallLogMapper callLogMapper;

    /** 数据 API Mapper（用于解析 api_id） */
    private final DataApiMapper dataApiMapper;

    /**
     * 记录一次开放 API 调用（best-effort，永不向调用方抛出异常）
     *
     * @param apiCode      API 编码
     * @param subscriber   订阅方；鉴权失败时可为空
     * @param authType     鉴权方式：API_KEY/TOKEN
     * @param status       结果：SUCCESS/FAILED
     * @param durationMs   调用耗时（毫秒）
     * @param rowCount     返回行数；Schema 探测或失败时为空
     * @param errorMessage 错误摘要（内部会脱敏并截断到 500）
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void record(String apiCode, String subscriber, String authType,
                       String status, long durationMs, Integer rowCount,
                       String errorMessage) {
        try {
            String code = normalizeApiCode(apiCode);
            DataApiCallLog entry = new DataApiCallLog();
            entry.setApiId(resolveApiId(code));
            entry.setApiCode(code);
            entry.setSubscriber(normalizeSubscriber(subscriber));
            entry.setAuthType(normalizeAuthType(authType));
            entry.setStatus(normalizeStatus(status));
            entry.setDurationMs(Math.max(0L, durationMs));
            entry.setRowCount(rowCount);
            entry.setErrorMessage(sanitizeError(errorMessage));
            entry.setCreatedAt(LocalDateTime.now());
            callLogMapper.insert(entry);
        } catch (Exception e) {
            // 审计失败绝不影响 API 响应；日志只记录异常类型与 API 编码
            log.warn("开放 API 调用审计写入失败: apiCode={}, errorType={}",
                    safeForLog(apiCode), e.getClass().getSimpleName());
        }
    }

    /**
     * 解析 api_id；API 不存在、已删除或查询失败时返回 0
     *
     * @param apiCode API 编码
     * @return 数据 API ID 或 0
     */
    private long resolveApiId(String apiCode) {
        if (apiCode == null || apiCode.isEmpty() || "-".equals(apiCode)) {
            return UNRESOLVED_API_ID;
        }
        try {
            DataApi api = dataApiMapper.selectByApiCode(apiCode);
            return api == null || api.getId() == null
                    ? UNRESOLVED_API_ID : api.getId();
        } catch (RuntimeException e) {
            // 单独兜底，保证即使解析 ID 失败也能写入审计记录
            log.debug("开放 API 审计解析 api_id 失败: apiCode={}, errorType={}",
                    safeForLog(apiCode), e.getClass().getSimpleName());
            return UNRESOLVED_API_ID;
        }
    }

    /**
     * 规范化 API 编码（非空且不超过 64 字符）
     *
     * @param apiCode 原始编码
     * @return 规范化后的编码
     */
    private String normalizeApiCode(String apiCode) {
        String value = trim(apiCode);
        if (value == null || value.isEmpty()) {
            return "-";
        }
        return value.length() > MAX_API_CODE_LENGTH
                ? value.substring(0, MAX_API_CODE_LENGTH) : value;
    }

    /**
     * 规范化订阅方（空值保留为 NULL，超长截断）
     *
     * @param subscriber 原始订阅方
     * @return 规范化后的订阅方
     */
    private String normalizeSubscriber(String subscriber) {
        String value = trim(subscriber);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() > MAX_SUBSCRIBER_LENGTH
                ? value.substring(0, MAX_SUBSCRIBER_LENGTH) : value;
    }

    /**
     * 规范化鉴权方式
     *
     * @param authType 原始值
     * @return API_KEY 或 TOKEN
     */
    private String normalizeAuthType(String authType) {
        return "API_KEY".equalsIgnoreCase(trim(authType)) ? "API_KEY" : "TOKEN";
    }

    /**
     * 规范化调用结果
     *
     * @param status 原始值
     * @return SUCCESS 或 FAILED
     */
    private String normalizeStatus(String status) {
        return "SUCCESS".equalsIgnoreCase(trim(status)) ? "SUCCESS" : "FAILED";
    }

    /**
     * 错误摘要脱敏并截断：移除密码、API Key、Bearer Token 与换行
     *
     * @param errorMessage 原始错误
     * @return 可落库的错误摘要；空值返回 null
     */
    private String sanitizeError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        String value = PASSWORD_ASSIGNMENT.matcher(errorMessage)
                .replaceAll("password=******");
        value = API_KEY_PATTERN.matcher(value).replaceAll("dam_******");
        value = BEARER_TOKEN_PATTERN.matcher(value).replaceAll("Bearer ******");
        value = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > MAX_ERROR_MESSAGE_LENGTH
                ? value.substring(0, MAX_ERROR_MESSAGE_LENGTH) : value;
    }

    /**
     * 日志字段脱敏：去除换行并限制长度，避免日志注入
     *
     * @param value 原始值
     * @return 单行、限长后的文本
     */
    private String safeForLog(String value) {
        if (value == null) {
            return null;
        }
        String singleLine = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return singleLine.length() > MAX_API_CODE_LENGTH
                ? singleLine.substring(0, MAX_API_CODE_LENGTH) : singleLine;
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
}
