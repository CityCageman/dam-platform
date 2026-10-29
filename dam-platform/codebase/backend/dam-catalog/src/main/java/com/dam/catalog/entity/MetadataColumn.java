package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 技术元数据-字段（对应文档 metadata_column，位于 dam_metadata 模块）
 * 字段级技术结构信息，与资产字段 dam_field（业务语义）职责分离
 */
@Data
@TableName(value = "metadata_column", schema = "dam_metadata")
public class MetadataColumn {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属数据源ID */
    private Long datasourceId;
    /** Schema ID */
    private Long schemaId;
    /** 所属表ID（metadata_table.id） */
    private Long tableId;
    private String columnName;
    private String columnType;
    private Long columnLength;
    /** 精度（metadata_column 表无 column_precision 列，仅作过渡字段，不参与持久化） */
    @TableField(exist = false)
    private Integer columnPrecision;
    private Boolean isNullable;
    private Boolean isPrimaryKey;
    private Boolean isForeignKey;
    /** 外键/指针引用表名（同步字段时从 JDBC getImportedKeys 获取，供血缘识别） */
    private String referenceTableName;
    private String defaultValue;
    private String columnComment;
    /** 字段顺序 */
    private Integer ordinalPosition;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
