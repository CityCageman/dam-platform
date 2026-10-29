package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "asset_tag_rel", schema = "dam_asset")
public class DamAssetTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    private Long tagId;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
