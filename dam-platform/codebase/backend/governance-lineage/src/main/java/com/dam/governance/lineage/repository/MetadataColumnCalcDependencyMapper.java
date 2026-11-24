package com.dam.governance.lineage.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.governance.lineage.entity.MetadataColumnCalcDependency;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段计算依赖表 Mapper
 */
@Mapper
public interface MetadataColumnCalcDependencyMapper extends BaseMapper<MetadataColumnCalcDependency> {
}
