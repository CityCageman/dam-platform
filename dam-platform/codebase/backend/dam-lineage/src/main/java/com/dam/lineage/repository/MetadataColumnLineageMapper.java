package com.dam.lineage.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.lineage.entity.MetadataColumnLineage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段级血缘关系表 Mapper
 */
@Mapper
public interface MetadataColumnLineageMapper extends BaseMapper<MetadataColumnLineage> {
}
