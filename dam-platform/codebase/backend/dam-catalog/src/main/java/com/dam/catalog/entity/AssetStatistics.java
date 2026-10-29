package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产使用统计（对应文档 asset_statistics，位于 dam_asset 模块）
 * 按日统计资产的浏览、查询、下载、导出次数与独立用户数
 */
@Data
@TableName(value = "asset_statistics", schema = "dam_asset")
public class AssetStatistics {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    /** 统计日期 */
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
}
