package com.dam.lifecycle.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "metadata_lifecycle_policy", schema = "dam_metadata")
public class DamLifecyclePolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 策略代码 */
    private String policyCode;
    /** 策略名 */
    private String policyName;
    /** 目标类型 */
    private String targetType;
    /** 目标ID */
    private Long targetId;
    /** 热数据保留天数 */
    private Integer hotDays;
    /** 冷数据保留天数 */
    private Integer coldDays;
    /** 归档触发天数 */
    private Integer archiveDays;
    /** 清理触发天数 */
    private Integer purgeDays;
    /** 冷存储路径 */
    private String coldStorageLocation;
    /** 归档存储路径 */
    private String archiveStorageLocation;
    /** 归档格式 */
    private String archiveFormat;
    /** 是否自动执行 */
    private Boolean autoExecute;
    /** 最后执行时间 */
    private LocalDateTime lastExecuteTime;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // ===== 兼容字段（前端使用 warmDays/retentionDays/archiveAction/assetType，Service 层转换填充，不参与持久化） =====
    @TableField(exist = false)
    private Integer warmDays;
    @TableField(exist = false)
    private Integer retentionDays;
    @TableField(exist = false)
    private String archiveAction;
    @TableField(exist = false)
    private String assetType;
}
