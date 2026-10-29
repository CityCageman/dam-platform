package com.dam.security.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "security_sensitive_rule", schema = "dam_security")
public class DamSensitiveRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 规则代码 */
    private String ruleCode;
    /** 规则名称 */
    private String ruleName;
    /** 敏感等级 */
    private String sensitivityLevel;
    /** 规则类型 */
    private String ruleType;
    /** 匹配模式 */
    private String rulePattern;
    /** 关联词典ID */
    private Long dictionaryId;
    private String description;
    /** 优先级 */
    private String priority;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private String sensitiveType;
    @TableField(exist = false)
    private String regexPattern;
    @TableField(exist = false)
    private String fieldNamePattern;
    @TableField(exist = false)
    private String level;
}
