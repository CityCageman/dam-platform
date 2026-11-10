package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "knowledge_category", schema = "dam_asset")
public class KnowledgeCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String categoryCode;
    private String categoryName;
    private String description;
    private Integer sortOrder;
    private Integer status;
    /** WeKnora 分类标签 ID */
    private String externalTagId;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
