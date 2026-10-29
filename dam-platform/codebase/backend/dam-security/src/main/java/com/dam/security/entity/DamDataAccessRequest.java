package com.dam.security.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据使用申请
 * 用户申请访问敏感数据的审批记录
 */
@Data
@TableName(value = "security_access_application", schema = "dam_security")
public class DamDataAccessRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 申请单号 */
    private String applicationNo;
    /** 申请人ID */
    private String applicantId;
    private Long assetId;
    /** 访问类型 */
    private String accessType;
    /** 申请理由 */
    private String accessReason;
    /** 有效期 */
    private String validityDays;
    /** 审批人ID */
    private String approverId;
    /** 审批意见 */
    private String approveComment;
    /** 审批时间 */
    private LocalDateTime approvedAt;
    /** 生效时间 */
    private LocalDateTime effectiveFrom;
    /** 失效时间 */
    private LocalDateTime effectiveTo;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Long userId;
    @TableField(exist = false)
    private String username;
    @TableField(exist = false)
    private String department;
    @TableField(exist = false)
    private String assetName;
    @TableField(exist = false)
    private String reason;
    @TableField(exist = false)
    private String requestType;
    @TableField(exist = false)
    private LocalDateTime approveTime;
}
