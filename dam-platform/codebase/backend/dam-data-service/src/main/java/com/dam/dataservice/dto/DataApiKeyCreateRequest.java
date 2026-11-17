package com.dam.dataservice.dto;

import lombok.Data;

/**
 * 创建数据服务 API Key 请求
 * <p>
 * {@code subscriber} 为空时使用当前平台登录用户；{@code expiresAt} 支持
 * {@code yyyy-MM-dd HH:mm:ss}、ISO-8601 日期时间（含时区偏移）或纯日期
 * {@code yyyy-MM-dd}。
 *
 * @author DAM Platform
 */
@Data
public class DataApiKeyCreateRequest {

    /** Key 名称 */
    private String name;

    /** 订阅方；为空时默认为当前登录用户 */
    private String subscriber;

    /** 过期时间；为空表示不过期 */
    private String expiresAt;
}
