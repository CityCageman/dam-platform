package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "asset_tag", schema = "dam_asset")
public class DamTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 标签代码 */
    private String tagCode;
    /** 标签名称 */
    private String tagName;
    /** 标签颜色 */
    private String tagColor;
    /** 标签范围：PUBLIC-公共，PRIVATE-私有 */
    private String tagScope;
    /** 标签类型：SYSTEM-系统预置，CUSTOM-用户自定义 */
    private String tagType;
    private String description;
    private Integer usageCount;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
