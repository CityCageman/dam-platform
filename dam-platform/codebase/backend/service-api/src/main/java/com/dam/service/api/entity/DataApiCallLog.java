package com.dam.service.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放 API 调用审计日志
 * <p>
 * 对应 {@code dam_dsp.data_api_call_log}。每次调用开放 API 无论成功或
 * 失败都写入一条记录；仅记录鉴权方式、结果、耗时、行数与脱敏后的错误摘要，
 * <b>不记录 API Key、密码与请求参数</b>。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "data_api_call_log", schema = "dam_dsp")
public class DataApiCallLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据 API ID；无法解析（如 API 不存在）时为 0 */
    private Long apiId;

    /** 数据 API 编码 */
    private String apiCode;

    /** 订阅方；鉴权失败时可为空 */
    private String subscriber;

    /** 鉴权方式：API_KEY/TOKEN */
    private String authType;

    /** 调用结果：SUCCESS/FAILED */
    private String status;

    /** 调用耗时（毫秒） */
    private Long durationMs;

    /** 返回行数；Schema 探测或失败时为空 */
    private Integer rowCount;

    /** 脱敏并截断后的错误摘要 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
