package com.dam.dataservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据服务 API Key
 * <p>
 * 对应 {@code dam_data_service.data_api_key}。数据库只保存 Key 的 SHA-256 摘要
 * （{@code key_hash}）与展示前缀（{@code key_prefix}），明文 Key 仅在创建接口
 * 响应中返回一次，不落库、不写日志。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "data_api_key", schema = "dam_data_service")
public class DataApiKey {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Key 展示前缀（不含密钥主体） */
    private String keyPrefix;

    /** Key 的 SHA-256 十六进制摘要 */
    private String keyHash;

    /** 订阅方（用于匹配 api_subscription.subscriber） */
    private String subscriber;

    /** Key 名称 */
    private String name;

    /** 状态：ACTIVE/REVOKED */
    private String status;

    /** 过期时间；NULL 表示不过期 */
    private LocalDateTime expiresAt;

    /** 最近一次用于鉴权的时间 */
    private LocalDateTime lastUsedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;
}
