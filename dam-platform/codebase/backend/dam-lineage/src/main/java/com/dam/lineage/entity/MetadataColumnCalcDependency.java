package com.dam.lineage.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.lineage.config.PgLongArrayTypeHandler;
import com.dam.lineage.config.PgStringArrayTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 字段计算依赖表（metadata_column_calc_dependency）
 * <p>
 * 专门记录复杂计算字段的依赖关系（如"金额 = 收费 - 退费"），支持多层计算追溯。
 * 每条记录挂接一条字段级血缘（metadata_column_lineage.id），
 * 以星型结构表达"1 个目标计算字段 → 多个源字段"。
 */
@Data
@TableName(value = "metadata_column_calc_dependency", schema = "dam_metadata")
public class MetadataColumnCalcDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字段级血缘ID（metadata_column_lineage.id） */
    private Long targetColumnLineageId;

    /** 被计算的目标字段ID（metadata_column.id） */
    private Long targetColumnId;

    /** 被计算的目标字段名（冗余） */
    private String targetColumnName;

    /** 计算类型（lineage_calc_type 字典：ARITHMETIC/AGGREGATE/CASE/FUNCTION/CONCAT） */
    private String calcTypeId;

    /** 计算表达式（如 fee_total - refund_total） */
    private String calcExpression;

    /** 依赖源字段ID列表（bigint[]，metadata_column.id） */
    @TableField(typeHandler = PgLongArrayTypeHandler.class)
    private List<Long> dependsOnColumnIds;

    /** 依赖源字段名列表（text[]） */
    @TableField(typeHandler = PgStringArrayTypeHandler.class)
    private List<String> dependsOnColumnNames;

    /** 计算操作符（lineage_operator 字典：+ - * / % ||） */
    private String dependencyOperatorId;

    /** 计算顺序（多层计算时从小到大依次执行） */
    private Integer calcOrder;

    /** 是否级联计算（依赖其他计算字段） */
    private Boolean isCascading;

    /** 描述 */
    private String description;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
