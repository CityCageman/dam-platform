package com.dam.catalog.entity;

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
}
