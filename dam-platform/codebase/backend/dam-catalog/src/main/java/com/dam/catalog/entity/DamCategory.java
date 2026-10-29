package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "asset_category", schema = "dam_asset")
public class DamCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 分类编码 */
    private String categoryCode;
    /** 分类名称 */
    private String categoryName;
    private Long parentId;
    /** 树层级 */
    private Long treeLevel;
    /** 分类全路径 */
    private String categoryPath;
    /** 排序序号 */
    private Long sortOrder;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // ===== 兼容字段（前端使用 name，Service 层返回时填充，不参与持久化） =====
    @TableField(exist = false)
    private String name;
    @TableField(exist = false)
    private String type;
}
