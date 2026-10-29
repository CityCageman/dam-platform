package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "asset_info", schema = "dam_asset")
public class DamAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 资产编码 */
    private String assetCode;
    /** 资产名称 */
    private String assetName;
    /** 资产类型ID（字典 asset_type 引用） */
    private Long assetTypeId;
    /** 所属分类ID */
    private Long categoryId;
    private String description;
    /** 业务负责人ID */
    private Long businessOwnerId;
    /** 技术负责人ID */
    private Long techOwnerId;
    /** 数据负责人ID */
    private Long dataOwnerId;
    /** 来源系统ID */
    private Long sourceSystemId;
    /** 数据源ID（与 metadata_datasource 强关联） */
    private Long dataSourceId;
    /** 元数据实体类型：TABLE/FILE/API/REPORT（多态外键关联技术元数据） */
    private String sourceMetadataType;
    /** 元数据实体ID（根据 source_metadata_type 关联 metadata_table/metadata_column） */
    private Long sourceMetadataId;
    /** 存储位置 */
    private String storageLocation;
    /** 数据行数 */
    @TableField("row_count")
    private Long recordCount;
    /** 存储大小（字节） */
    @TableField("storage_size")
    private Long storageSize;
    /** 质量评分 */
    private BigDecimal qualityScore;
    /** 热度（访问次数） */
    private Integer popularity;
    /** 发布状态：1-已发布，2-草稿，3-已下线 */
    private Integer publishedStatus;
    /** 发布时间 */
    private LocalDateTime publishedAt;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    // ===== 过渡字段（新表无对应列，仅兼容旧 Service 引用，不参与持久化） =====
    @TableField(exist = false)
    private String assetType;
    @TableField(exist = false)
    private String sourceSystem;
    @TableField(exist = false)
    private String databaseName;
    @TableField(exist = false)
    private String schemaName;
    @TableField(exist = false)
    private String subjectDomain;
    @TableField(exist = false)
    private String tableName;
    @TableField(exist = false)
    private Long ownerId;
    @TableField(exist = false)
    private String department;
    @TableField(exist = false)
    private String securityLevel;
    @TableField(exist = false)
    private String partitionField;
    /** 所属类目（多主题域）：[{subjectDomainId,subjectDomainName,categoryId,categoryName}] */
    @TableField(exist = false)
    private List<Map<String, Object>> categories;
    /** 分类展示文本，如「医疗数据主题域/临床数据」多个用；分隔 */
    @TableField(exist = false)
    private String categoryPathText;
}
