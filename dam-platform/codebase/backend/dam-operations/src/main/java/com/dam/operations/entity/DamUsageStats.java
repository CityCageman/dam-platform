package com.dam.operations.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "asset_statistics", schema = "dam_asset")
public class DamUsageStats {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 资产ID */
    private Long assetId;
    /** 统计时间 */
    private LocalDateTime statDate;
    /** 浏览次数 */
    private Integer viewCount;
    /** 查询次数 */
    private Integer queryCount;
    /** 下载次数 */
    private Integer downloadCount;
    /** 导出次数 */
    private Integer exportCount;
    /** 独立用户数 */
    private Integer uniqueUsers;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Integer apiCallCount;
    @TableField(exist = false)
    private Integer downstreamCount;
    @TableField(exist = false)
    private Integer favoriteCount;
    @TableField(exist = false)
    private java.math.BigDecimal avgRating;
    @TableField(exist = false)
    private java.math.BigDecimal businessValueScore;
    @TableField(exist = false)
    private java.math.BigDecimal storageCost;
    @TableField(exist = false)
    private java.math.BigDecimal computeCost;
    @TableField(exist = false)
    private java.math.BigDecimal totalScore;
}
