package com.dam.security.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "security_data_audit_log", schema = "dam_security")
public class DamAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 链路追踪ID */
    private String traceId;
    /** 会话ID */
    private String sessionId;
    /** 操作人ID */
    private String operatorId;
    /** 操作人IP地址 */
    private String operatorIp;
    /** 资产ID */
    private Long assetId;
    private String assetName;
    private Long tableId;
    private String tableName;
    /** 操作类型 */
    private String operationType;
    /** 执行的SQL语句 */
    private String sqlContent;
    /** 过滤/查询条件 */
    private String filterCondition;
    /** 影响/返回行数 */
    private Long affectedRows;
    /** 返回数据量大小 */
    private Long dataSize;
    /** 最高敏感等级 */
    private String sensitivityLevel;
    /** 是否应用脱敏 */
    private Boolean maskingApplied;
    /** 使用的权限标识 */
    private String permissionCode;
    /** 访问原因 */
    private String accessReason;
    /** 是否授权访问 */
    private Boolean isApproved;
    /** 风险等级 */
    private String riskLevel;
    /** 操作时间 */
    private LocalDateTime operationTime;
    private LocalDateTime createdAt;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Long userId;
    @TableField(exist = false)
    private String username;
    @TableField(exist = false)
    private String action;
    @TableField(exist = false)
    private String targetType;
    @TableField(exist = false)
    private Long targetId;
    @TableField(exist = false)
    private String detail;
    @TableField(exist = false)
    private String ipAddress;
    @TableField(exist = false)
    private String userAgent;
    @TableField(exist = false)
    private String status;
}
