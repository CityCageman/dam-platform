package com.dam.standard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 标准偏差报告
 * 记录资产与标准之间的偏差
 */
@Data
@TableName(value = "standard_deviation_report", schema = "dam_standard")
public class DamDeviationReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 报告编号 */
    private String reportNo;
    /** 检查类型 */
    private String checkType;
    /** 关联表ID */
    private Long tableId;
    /** 偏差字段名 */
    private String columnName;
    /** 关联标准码表ID */
    private Long standardCodeTableId;
    /** 期望值 */
    private String expectedValue;
    /** 实际值 */
    private String actualValue;
    /** 偏差描述 */
    private String deviationDescription;
    /** 严重程度 */
    private String severity;
    /** 修复建议 */
    private String fixSuggestion;
    /** 修复人ID */
    private String fixedBy;
    /** 修复时间 */
    private LocalDateTime fixedAt;
    /** 检查时间 */
    private LocalDateTime checkTime;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Long stdId;
    @TableField(exist = false)
    private String stdName;
    @TableField(exist = false)
    private Long assetId;
    @TableField(exist = false)
    private String assetName;
    @TableField(exist = false)
    private String fieldName;
    @TableField(exist = false)
    private String deviationType;
    @TableField(exist = false)
    private Long ticketId;
    @TableField(exist = false)
    private LocalDateTime checkedAt;
}
