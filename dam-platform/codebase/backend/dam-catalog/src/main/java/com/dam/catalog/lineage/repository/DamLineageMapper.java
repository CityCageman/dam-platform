package com.dam.catalog.lineage.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.lineage.entity.DamLineage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DamLineageMapper extends BaseMapper<DamLineage> {
}
