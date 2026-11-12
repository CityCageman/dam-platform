package com.dam.aiworkbench.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 任务中心-任务
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "ai_task", schema = "dam_asset")
public class AiTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务类型：COMMENT/CLASSIFY/LINEAGE/REPORT */
    private String taskType;

    /** 任务标题 */
    private String title;

    /** 目标类型：DATASOURCE/TABLE/ASSET */
    private String targetType;

    /** 目标ID */
    private Long targetId;

    /** 状态：PENDING/RUNNING/REVIEW/DONE/FAILED */
    private String status;

    /** 明细总数 */
    private Integer totalCount;

    /** 已采纳数 */
    private Integer acceptedCount;

    /** 已驳回数 */
    private Integer rejectedCount;

    /** 模型 */
    private String model;

    /** 提示词/上下文 */
    private String prompt;

    /** 失败原因 */
    private String errorMessage;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
