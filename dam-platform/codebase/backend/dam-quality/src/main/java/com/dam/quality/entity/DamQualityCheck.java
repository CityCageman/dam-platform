package com.dam.quality.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName(value = "metadata_quality_check_result", schema = "dam_metadata")
public class DamQualityCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private LocalDateTime checkTime;
    private Integer totalCount;
    /** 通过记录数 */
    private Integer passCount;
    /** 不通过记录数 */
    private Integer failCount;
    /** 通过率 */
    private java.math.BigDecimal passRate;
    /** 结果状态 */
    private String resultStatus;
    /** 失败样例 */
    @TableField(typeHandler = com.dam.catalog.config.JsonbTypeHandler.class)
    private String failSamples;
    /** 错误详情 */
    private String errorDetail;
    /** 执行人 */
    private String executedBy;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Integer errorCount;
    @TableField(exist = false)
    private java.math.BigDecimal errorRate;
    @TableField(exist = false)
    private java.math.BigDecimal score;
    @TableField(exist = false)
    private String detailJson;
}
