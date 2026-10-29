package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产-主题域-类目归属（dam_asset.asset_category_rel）
 * <p>
 * 规则：同一资产可归属多个主题域（subject_domain_id，一级分类），
 * 但在同一主题域下只能归属一个类目（category_id，二级分类），
 * 由唯一索引 uk_asset_domain_rel(asset_id, subject_domain_id) 保证。
 */
@Data
@TableName(value = "asset_category_rel", schema = "dam_asset")
public class AssetCategoryRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资产ID（asset_info.id） */
    private Long assetId;

    /** 一级主题域ID（asset_category.tree_level=1） */
    private Long subjectDomainId;

    /** 二级类目ID（asset_category.tree_level=2） */
    private Long categoryId;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
