package com.dam.security.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "security_masking_policy", schema = "dam_security")
public class DamMaskingPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 策略编码 */
    private String policyCode;
    /** 策略名称 */
    private String policyName;
    /** 目标类型 */
    private String targetType;
    /** 目标ID */
    private Long targetId;
    /** 目标字段名 */
    private String targetColumn;
    /** 脱敏类型 */
    private String maskingType;
    /** 脱敏配置 */
    @TableField(typeHandler = com.dam.catalog.config.JsonbTypeHandler.class)
    private String maskingConfig;
    /** 优先级 */
    private Integer priority;
    /** 应用场景 */
    private String applyScenario;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private String sensitiveType;
    @TableField(exist = false)
    private String maskingAlgorithm;
    @TableField(exist = false)
    private String maskingParam;
}
