package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.catalog.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "knowledge_entry", schema = "dam_asset")
public class KnowledgeEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String title;
    private String content;
    /** MANUAL / METADATA / METRIC / STANDARD */
    private String sourceType;
    private String sourceId;
    private String sourceKey;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String tags;

    private String vectorId;
    /** WeKnora 外部知识库/文档 ID */
    private String externalKbId;
    /** TERM / METRIC / STANDARD / CASE */
    private String entryType;
    private String definition;
    private String usageNote;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String aliases;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String fieldMappings;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String relatedMetrics;

    private String sourceSystem;
    private String owner;
    /** DRAFT / PUBLISHED / DEPRECATED */
    private String publishStatus;
    private Integer version;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    private String externalDocId;
    private String externalSummary;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String externalMetadata;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String externalTags;

    private String externalParseStatus;
    private LocalDateTime externalUpdatedAt;
    /** LOCAL_ONLY / SYNCED / FAILED */
    private String syncStatus;
    private LocalDateTime lastSyncedAt;
    private String lastError;
    /** PENDING / SYNCED / FAILED */
    private String embeddingStatus;
    private Integer enabled;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
