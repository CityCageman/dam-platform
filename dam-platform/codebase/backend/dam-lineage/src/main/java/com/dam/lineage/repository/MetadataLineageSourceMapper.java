package com.dam.lineage.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.lineage.entity.MetadataLineageSource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 血缘节点注册表 Mapper
 */
@Mapper
public interface MetadataLineageSourceMapper extends BaseMapper<MetadataLineageSource> {
}
