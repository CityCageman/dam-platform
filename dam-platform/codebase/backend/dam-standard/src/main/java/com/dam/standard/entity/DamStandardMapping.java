package com.dam.standard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "standard_mapping", schema = "dam_standard")
public class DamStandardMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 源系统ID */
    private Long sourceSystemId;
    /** 源表ID */
    private Long sourceTableId;
    /** 源字段 */
    private String sourceColumn;
    /** 源码值 */
    private String sourceValue;
    /** 标准码表ID */
    private Long standardCodeTableId;
    /** 标准码值 */
    private String standardCodeValue;
    /** 映射规则 */
    private String mappingRule;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Long stdId;
    @TableField(exist = false)
    private Long assetId;
    @TableField(exist = false)
    private Long fieldId;
    @TableField(exist = false)
    private String mappingStatus;
    @TableField(exist = false)
    private String mappedValue;
    @TableField(exist = false)
    private java.math.BigDecimal coverageRate;
}
