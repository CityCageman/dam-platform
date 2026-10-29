package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.catalog.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "metadata_management", schema = "dam_metadata")
public class DamMetadataExt {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联技术元数据表ID（兼容旧 assetId 语义） */
    private Long tableId;
    @TableField(exist = false)
    private Long assetId;
    /** 扩展属性类别（etl/storage/quality/system） */
    @TableField(exist = false)
    private String category;
    /** 扩展属性键 */
    @TableField(exist = false)
    private String propKey;
    /** 扩展属性值 */
    @TableField(exist = false)
    private String propValue;
    @TableField(exist = false)
    private Integer sortOrder;
    /** 扩展属性JSON（metadata_management.extend_props 承载） */
    @TableField(value = "extend_props", typeHandler = JsonbTypeHandler.class)
    private String extendProps;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
