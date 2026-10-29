package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 技术元数据-表（对应文档 metadata_table，位于 dam_metadata 模块）
 * 与资产目录 dam_asset 职责分离：本表只存扫描到的技术结构信息，资产治理信息存 dam_asset
 */
@Data
@TableName(value = "metadata_table", schema = "dam_metadata")
public class MetadataTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属数据源ID */
    private Long datasourceId;
    /** 所属Schema ID */
    private Long schemaId;
    /** Schema名称（表无 schema_name 列，仅作过渡字段，不参与持久化；schema 归属经 schema_id 关联 metadata_schema） */
    @TableField(exist = false)
    private String schemaName;
    private String tableName;
    private String tableComment;
    /**
     * 主题域（数据域），标识表所属的业务主题。
     * 通用主题域：PATIENT-患者域，CLINICAL-临床域，TREATMENT-诊疗域，SURGERY-手术域，MEDICATION-药品域，
     * FINANCE-费用域，ADMIN-管理域，REFERENCE-基础数据域，LOG-日志域，WORKFLOW-流程域；
     * 基础字典主题域（DIM 维度层字典表）：DIM_Pharmacy-药品与药事管理域，DIM_Encounter-就诊与挂号域，
     * DIM_Order-医嘱与执行域，DIM_DiagnosisSurgery-诊断与手术域，DIM_Patient-患者与人口学域，
     * DIM_Organization-组织架构与人员域，DIM_Finance-财务与收费域，DIM_LabExam-检验与检查域，
     * DIM_SupplyChain-供应链与物资域，DIM_MedicalRecord-病案与质控域，DIM_TimeDuration-基础治疗与疗程域，
     * DIM_Security-系统权限与安全域
     */
    private String subjectDomain;
    /** 表类型：TABLE/VIEW */
    private String tableType;
    /** 行数 */
    private Long rowCount;
    /** 表大小（字节） */
    private Long tableSize;
    private LocalDateTime createdTime;
    private LocalDateTime lastModified;
    private LocalDateTime lastSyncTime;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
