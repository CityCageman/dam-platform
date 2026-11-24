package com.dam.governance.lineage.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.governance.lineage.entity.MetadataLineageSource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 血缘节点注册表 Mapper
 */
@Mapper
public interface MetadataLineageSourceMapper extends BaseMapper<MetadataLineageSource> {
}
