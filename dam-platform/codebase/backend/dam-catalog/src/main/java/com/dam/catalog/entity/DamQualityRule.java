package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "metadata_quality_rule", schema = "dam_metadata")
public class DamQualityRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 规则编码 */
    private String ruleCode;
    /** 规则名称 */
    private String ruleType;
    /** 级别 */
    private String ruleLevel;
    /** 目标表ID */
    private Long targetTableId;
    /** 目标字段名 */
    private String targetColumn;
    /** 规则表达式 */
    private String ruleExpression;
    /** 期望值 */
    private String expectValue;
    /** 严重程度 */
    private String severity;
    /** 是否定时执行 */
    private Boolean isScheduled;
    /** 定时表达式 */
    private String cronExpression;
    /** 规则描述 */
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
