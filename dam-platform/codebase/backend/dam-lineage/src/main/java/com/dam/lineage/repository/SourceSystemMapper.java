package com.dam.lineage.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.lineage.entity.SourceSystem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 来源系统 Mapper（只读，用于血缘节点"所属系统"回填）
 */
@Mapper
public interface SourceSystemMapper extends BaseMapper<SourceSystem> {
}
