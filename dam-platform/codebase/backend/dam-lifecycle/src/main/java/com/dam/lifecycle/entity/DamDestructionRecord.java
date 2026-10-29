package com.dam.lifecycle.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "metadata_destruction_record", schema = "dam_metadata")
public class DamDestructionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 销毁单号 */
    private String destructionNo;
    /** 目标类型 */
    private String targetType;
    /** 目标ID */
    private Long targetId;
    /** 目标名称 */
    private String targetName;
    /** 销毁理由 */
    private String destructionReason;
    /** 范围 */
    private String destructionScope;
    /** 筛选条件 */
    private String filterCondition;
    /** 影响行数 */
    private Integer affectedRows;
    /** 销毁方法 */
    private String destructionMethod;
    /** 申请人ID */
    private String requesterId;
    /** 审批人ID */
    private String approverId;
    /** 执行人ID */
    private String executorId;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime executedAt;
    private String remark;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
