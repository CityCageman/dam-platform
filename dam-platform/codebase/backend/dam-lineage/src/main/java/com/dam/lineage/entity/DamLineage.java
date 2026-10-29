package com.dam.lineage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "metadata_lineage", schema = "dam_metadata")
public class DamLineage {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 源节点类型 */
    private String sourceType;
    /** 源节点ID */
    private Long sourceId;
    /** 目标节点类型 */
    private String targetType;
    /** 目标节点ID */
    private Long targetId;
    /** 关系类型 */
    private String relationType;
    /** 转换规则 */
    private String transformRule;
    /** 依赖层级 */
    private Integer dependencyLevel;
    /** 来源系统标识 */
    private String sourceSystem;
    /** 关联采集记录ID */
    private Long collectionId;
    /** 发现方式 */
    private String discoveredBy;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    /** 删除时间（@TableLogic 逻辑删除：未删除为 NULL，删除置 now()；deleted_at 为 TIMESTAMP 列不能用默认 0/1 比较） */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Long sourceAssetId;
    @TableField(exist = false)
    private Long sourceFieldId;
    @TableField(exist = false)
    private Long targetAssetId;
    @TableField(exist = false)
    private Long targetFieldId;
    @TableField(exist = false)
    private String transformSql;
    @TableField(exist = false)
    private String jobId;
    @TableField(exist = false)
    private String jobName;
    @TableField(exist = false)
    private String lineageType;
    @TableField(exist = false)
    private String targetSystem;
    // ===== 节点名称（exist=false，查询时经 lineage_source 回填） =====
    @TableField(exist = false)
    private String sourceNodeName;
    @TableField(exist = false)
    private String targetNodeName;
}
