package com.dam.lineage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分层血缘关系表（metadata_lineage_hierarchy）
 * <p>
 * 记录应用层/逻辑层/物理层的分层依赖关系，支持多层级血缘追溯。
 * 上下游节点经 lineage_source.id 关联，可细化到表级/字段级（table_id/column_id）。
 */
@Data
@TableName(value = "metadata_lineage_hierarchy", schema = "dam_metadata")
public class MetadataLineageHierarchy {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上游节点ID（metadata_lineage_source.id） */
    private Long upstreamSourceId;

    /** 上游节点类型（lineage_node_type 字典） */
    private String upstreamTypeId;

    /** 上游表ID（metadata_table.id，可空） */
    private Long upstreamTableId;

    /** 上游字段ID（metadata_column.id，可空） */
    private Long upstreamColumnId;

    /** 下游节点ID（metadata_lineage_source.id） */
    private Long downstreamSourceId;

    /** 下游节点类型（lineage_node_type 字典） */
    private String downstreamTypeId;

    /** 下游表ID（metadata_table.id，可空） */
    private Long downstreamTableId;

    /** 下游字段ID（metadata_column.id，可空） */
    private Long downstreamColumnId;

    /** 分层层级（lineage_layer 字典：1-应用层/2-逻辑层/3-物理层/4-字典层） */
    private Integer layerLevel;

    /** 关系类型（relation_type 字典：FLOW/COPY/TRANSFORM/DEPEND/REFERENCE/GENERATE） */
    private String relationTypeId;

    /** 依赖深度（直接依赖=1，逐层+1） */
    private Integer dependencyDepth;

    /** 是否直接依赖 */
    private Boolean isDirectDependency;

    /** 转换规则（SQL/转换逻辑） */
    private String transformRule;

    /** 转换类型（lineage_transform_type 字典） */
    private String transformTypeId;

    /** 血缘路径（如 REPORT:A → TABLE:B → DICT:C，逗号分隔节点编码） */
    private String lineagePath;

    /** 来源系统ID */
    private Long sourceSystemId;

    /** 关联采集记录ID */
    private Long collectionId;

    /** 发现方式（discovered_by 字典：PARSER/MANUAL/IMPORT） */
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
}
