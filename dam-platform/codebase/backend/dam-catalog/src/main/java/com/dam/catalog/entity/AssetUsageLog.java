package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.catalog.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 资产使用日志（对应文档 asset_usage_log，位于 dam_asset 模块）
 * 记录资产的浏览、查询、下载、导出等操作行为
 */
@Data
@TableName(value = "asset_usage_log", schema = "dam_asset")
public class AssetUsageLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    /** 用户ID */
    private String userId;
    /** 操作类型: view/query/download/export/favorite */
    private String actionType;
    /** 操作详情 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String actionDetail;
    private String ipAddress;
    private String userAgent;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
