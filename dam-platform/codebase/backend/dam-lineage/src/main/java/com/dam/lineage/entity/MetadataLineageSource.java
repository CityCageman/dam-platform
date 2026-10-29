package com.dam.lineage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 血缘节点注册表（metadata_lineage_source）
 * <p>
 * 统一注册血缘中涉及的各类节点（报表/视图/存储过程/数据表/基础字典表/ETL作业），
 * 血缘与资产不强关联：source_code 唯一标识节点，可独立于资产存在。
 */
@Data
@TableName(value = "metadata_lineage_source", schema = "dam_metadata")
public class MetadataLineageSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 节点编码（唯一标识，如报表编码/表名/作业名） */
    private String sourceCode;

    /** 节点名称（展示名） */
    private String sourceName;

    /** 节点类型（lineage_node_type 字典：REPORT/VIEW/PROCEDURE/TABLE/DICT/ETL_JOB/CTE） */
    private String sourceTypeId;

    /** 节点路径（如报表文件路径/SQL脚本路径） */
    private String sourcePath;

    /** 节点内容（如存储过程定义/ETL配置/报表SQL原文） */
    private String sourceContent;

    /** 负责人 */
    private String sourceOwner;

    /** 来源系统ID（dam_source_system.id，可空） */
    private Long sourceSystemId;

    /** 资产分类ID（dam_asset.asset_category.id，可空；用于分类树组织血缘节点） */
    private Long categoryId;

    /** 描述 */
    private String description;

    /** 来源系统名称（exist=false，列表查询时经 source_system 回填） */
    @TableField(exist = false)
    private String sourceSystemName;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
