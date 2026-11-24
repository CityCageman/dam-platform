package com.dam.service.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据服务 API Key 视图
 * <p>
 * 仅包含可安全展示的元数据，<b>不包含明文 Key 与 SHA-256 摘要</b>。
 *
 * @author DAM Platform
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataApiKeyView {

    /** Key 展示前缀 */
    private String prefix;

    /** Key 名称 */
    private String name;

    /** 订阅方 */
    private String subscriber;

    /** 状态：ACTIVE/REVOKED */
    private String status;

    /** 过期时间；NULL 表示不过期 */
    private LocalDateTime expiresAt;

    /** 最近使用时间 */
    private LocalDateTime lastUsedAt;
}
