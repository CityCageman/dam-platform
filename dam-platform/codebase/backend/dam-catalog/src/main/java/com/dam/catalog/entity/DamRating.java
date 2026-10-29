package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "asset_rating", schema = "dam_asset")
public class DamRating {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 评价人ID */
    private String userId;
    private Long assetId;
    /** 资产评分（1~5） */
    private Integer ratingScore;
    /** 评价内容 */
    private String ratingComment;
    /** 评价维度 */
    private String ratingDimension;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // ===== 兼容字段（前端使用 score/comment，Service 层转换填充，不参与持久化） =====
    @TableField(exist = false)
    private Integer score;
    @TableField(exist = false)
    private String comment;
}
