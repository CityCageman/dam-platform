package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "metadata_column", schema = "dam_metadata")
public class DamField {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 资产ID（兼容旧逻辑，实际通过 asset_info.source_metadata_id 关联 metadata_table） */
    @TableField(exist = false)
    private Long assetId;
    /** 所属表ID（metadata_table.id） */
    private Long tableId;
    /** 所属数据源ID */
    private Long datasourceId;
    /** Schema ID（metadata_column.schema_id 为 NOT NULL） */
    private Long schemaId;
    /** 字段名（映射新表真实列 column_name） */
    @TableField("column_name")
    private String fieldName;
    @TableField(exist = false)
    private String fieldAlias;
    /** 字段类型（映射新表真实列 column_type） */
    @TableField("column_type")
    private String dataType;
    /** 字段长度（映射新表真实列 column_length） */
    @TableField("column_length")
    private Integer dataLength;
    /** 是否主键（映射新表真实列 is_primary_key，BOOLEAN） */
    @TableField("is_primary_key")
    private Boolean isPrimaryKey;
    /** 是否可空（映射新表真实列 is_nullable，BOOLEAN） */
    @TableField("is_nullable")
    private Boolean isNullable;
    private String defaultValue;
    /** 字段注释（映射新表真实列 column_comment） */
    @TableField("column_comment")
    private String comment;
    /** 安全等级（映射新表真实列 security_level：public/internal/confidential/restricted/critical） */
    @TableField("security_level")
    private String securityLevel;
    @TableField(exist = false)
    private Integer isSensitive;
    /** 敏感类型（映射新表真实列 sensitive_type，字典 sensitive_type） */
    @TableField("sensitive_type")
    private String sensitiveType;
    /** 外键/指针引用表名（映射新表真实列 reference_table_name） */
    @TableField("reference_table_name")
    private String referenceTableName;
    /** 字段顺序（映射新表真实列 ordinal_position） */
    @TableField("ordinal_position")
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
