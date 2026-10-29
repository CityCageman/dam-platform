package com.dam.lineage.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.lineage.config.JsonbTypeHandler;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 字段级血缘关系表（metadata_column_lineage）
 * <p>
 * 精细管理字段到字段的血缘映射：记录来源字段、转换规则、关联条件、过滤条件、聚合方式。
 * 每条记录挂接一条分层血缘边（metadata_lineage_hierarchy.id），实现"宏观→微观"钻取。
 */
@Data
@TableName(value = "metadata_column_lineage", schema = "dam_metadata")
public class MetadataColumnLineage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分层血缘边ID（metadata_lineage_hierarchy.id） */
    private Long hierarchyId;

    /** 源表ID（metadata_table.id） */
    private Long sourceTableId;

    /** 源字段ID（metadata_column.id） */
    private Long sourceColumnId;

    /** 源字段名（冗余存储，便于展示） */
    private String sourceColumnName;

    /** 目标表ID（metadata_table.id） */
    private Long targetTableId;

    /** 目标字段ID（metadata_column.id） */
    private Long targetColumnId;

    /** 目标字段名（冗余存储，便于展示） */
    private String targetColumnName;

    /** 映射类型（lineage_mapping_type 字典：DIRECT/CALC/JOIN/FILTER/AGGREGATE/FUNCTION） */
    private String mappingTypeId;

    /** 转换表达式（如 fee_total - refund_total） */
    private String transformExpression;

    /** 转换函数ID（如 SUM/AVG/COUNT/CONCAT 等） */
    private String transformFunctionId;

    /** 转换参数（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String transformParams;

    /** 关联条件（JOIN 条件，如 v.dept_id = d.id） */
    private String joinCondition;

    /** 过滤条件（WHERE 条件） */
    private String filterCondition;

    /** 聚合类型（lineage_aggregation_type 字典：SUM/AVG/COUNT/MAX/MIN） */
    private String aggregationTypeId;

    /** 是否为主数据来源 */
    private Boolean isPrimarySource;

    /** 置信度（0-1） */
    private BigDecimal confidenceScore;

    /** 血缘路径 */
    private String lineagePath;

    /** 关联采集记录ID */
    private Long collectionId;

    /** 发现方式（discovered_by 字典） */
    private String discoveredById;

    /** 描述 */
    private String description;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;

    // ===== 节点名称（exist=false，查询时经 lineage_source 回填） =====
    @TableField(exist = false)
    private String sourceTableName;
    @TableField(exist = false)
    private String targetTableName;
}
