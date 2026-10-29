package com.dam.security.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * ABAC数据权限策略
 * 定义：谁(角色/部门)能对什么密级的数据做什么操作
 */
@Data
@TableName(value = "security_data_permission", schema = "dam_security")
public class DamDataPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 权限编码 */
    private String permissionCode;
    /** 权限名称 */
    private String permissionName;
    /** 资源类型 */
    private String resourceType;
    /** 资源ID */
    private Long resourceId;
    /** 主体类型 */
    private String subjectType;
    /** 主体ID */
    private String subjectId;
    /** 操作类型 */
    private String actionType;
    /** 行级过滤条件 */
    private String filterCondition;
    /** 列级掩码 */
    private String columnMask;
    /** 优先级 */
    private Integer priority;
    /** 生效起始时间 */
    private LocalDateTime effectiveFrom;
    /** 生效结束时间 */
    private LocalDateTime effectiveTo;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private String policyName;
    @TableField(exist = false)
    private String roleName;
    @TableField(exist = false)
    private String department;
    @TableField(exist = false)
    private String securityLevel;
    @TableField(exist = false)
    private String action;
    @TableField(exist = false)
    private String effect;
}
