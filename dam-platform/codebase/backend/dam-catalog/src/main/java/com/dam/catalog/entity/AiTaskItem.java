package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 任务中心-明细草稿
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "ai_task_item", schema = "dam_asset")
public class AiTaskItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务 */
    private Long taskId;

    /** 明细类型：COMMENT/CLASSIFY/LINEAGE/REPORT */
    private String itemType;

    /** 目标ID（表/资产） */
    private Long targetId;

    /** 目标名称（表名等） */
    private String targetName;

    /** 字段名（字段级任务） */
    private String fieldName;

    /** 草稿内容（注释/分类/关系等） */
    private String draftContent;

    /** 置信度 */
    private BigDecimal confidence;

    /** 状态：DRAFT/ACCEPTED/REJECTED */
    private String status;

    private String reviewedBy;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;
}
