package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 技术元数据-模式（对应文档 metadata_schema，位于 dam_metadata 模块）
 * 记录数据源下的 Schema 信息（数据库/目录/虚拟模式）
 */
@Data
@TableName(value = "metadata_schema", schema = "dam_metadata")
public class MetadataSchema {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属数据源ID */
    private Long datasourceId;
    private String schemaName;
    private String schemaComment;
    /** Schema类型（如：DATABASE、CATALOG、VIRTUAL） */
    private String schemaType;
    /** Schema负责人ID */
    private String schemaOwner;
    /** 环境标识：PROD-生产、TEST-测试、DEV-开发 */
    private String environment;
    /** 物理存储路径 */
    private String location;
    /** 扩展属性（JSON格式） */
    private String properties;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
