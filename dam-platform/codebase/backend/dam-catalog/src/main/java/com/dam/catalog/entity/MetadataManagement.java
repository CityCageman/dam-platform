package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.catalog.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理元数据（对应文档 metadata_management，位于 dam_metadata 模块）
 * 记录治理状态、生命周期、安全分级等管理属性，通过 table_id 关联技术元数据表
 */
@Data
@TableName(value = "metadata_management", schema = "dam_metadata")
public class MetadataManagement {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联技术元数据表ID */
    private Long tableId;
    /** 治理状态id */
    private String governanceStatusId;
    /** 生命周期状态id */
    private String lifecycleStatusId;
    /** 数据保留天数 */
    private Long retentionDays;
    /** 数据来源系统id */
    private Long sourceSystemId;
    /** ETL/数据集成工作流名称 */
    private String etlWorkflow;
    private LocalDateTime lastAccessedAt;
    /** 日均查询次数 */
    private Long dailyQueryAvg;
    /** 安全分级id */
    private String securityClassification;
    /** 数据验证状态id */
    private String dataVerificationStatus;
    /** 注册批次ID */
    private String registrationBatchId;
    /** 表结构变更时间 */
    private LocalDateTime lastDdlTime;
    /** 审计备注 */
    private String auditRemark;
    /** 扩展属性 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String extendProps;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
