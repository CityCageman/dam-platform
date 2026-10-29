package com.dam.lineage.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.lineage.entity.MetadataLineageHierarchy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分层血缘关系表 Mapper
 */
@Mapper
public interface MetadataLineageHierarchyMapper extends BaseMapper<MetadataLineageHierarchy> {
}
