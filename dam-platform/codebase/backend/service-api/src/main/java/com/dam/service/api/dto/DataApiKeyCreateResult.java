package com.dam.service.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 创建数据服务 API Key 的结果
 * <p>
 * 明文 {@code key} 只在创建响应中返回一次，后续查询接口不再返回。{@code id}
 * 用于调用撤销接口 {@code POST /api/data-service/api-keys/{id}/revoke}。
 *
 * @author DAM Platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataApiKeyCreateResult {

    /** 主键，用于后续撤销 */
    private Long id;

    /** 明文 Key（仅本次返回，不落库） */
    private String key;

    /** Key 展示前缀 */
    private String prefix;

    /** Key 名称 */
    private String name;

    /** 订阅方 */
    private String subscriber;

    /** 状态 */
    private String status;

    /** 过期时间 */
    private LocalDateTime expiresAt;
}
