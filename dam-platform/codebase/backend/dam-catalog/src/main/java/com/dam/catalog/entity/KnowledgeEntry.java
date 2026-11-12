package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.common.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "knowledge_entry", schema = "dam_asset")
public class KnowledgeEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String title;
    /** 指标解释关联的指标库指标；METRIC 类型必填 */
    private Long metricId;
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

    /** 该知识条目的参考 SQL（只作为口径示例，不作为直接执行依据） */
    private String sqlExample;

    /** 取数规则：粒度、去重、必用表、条件、主报表/下钻模板等 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String reportSpec;

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
