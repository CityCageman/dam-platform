package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "model_column_mapping", schema = "dam_model")
public class DamModelField {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 模型ID */
    private Long modelId;
    /** 模型类型：FACT-事实，DIMENSION-维度 */
    private String modelType;
    /** 源表ID */
    private Long sourceTableId;
    /** 源字段ID */
    private Long sourceColumnId;
    /** 目标字段 */
    private String targetColumnName;
    /** 目标字段类型 */
    private String targetColumnType;
    /** 是否主键：0-否，1-是 */
    private Integer isPrimaryKey;
    /** 是否外键：0-否，1-是 */
    private Integer isForeignKey;
    /** 引用维度ID */
    private Long refDimensionId;
    /** 转换规则 */
    private String transformRule;
    /** 排序序号 */
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // ===== 兼容字段（前端使用 fieldName/fieldAlias/dataType/dataLength/dataPrecision/isNullable，Service 层转换填充，不参与持久化） =====
    @TableField(exist = false)
    private String fieldName;
    @TableField(exist = false)
    private String fieldAlias;
    @TableField(exist = false)
    private String dataType;
    @TableField(exist = false)
    private Integer dataLength;
    @TableField(exist = false)
    private Integer dataPrecision;
    @TableField(exist = false)
    private Integer isNullable;
}
