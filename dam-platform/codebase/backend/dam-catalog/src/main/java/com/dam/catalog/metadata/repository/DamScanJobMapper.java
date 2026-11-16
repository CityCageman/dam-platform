package com.dam.catalog.metadata.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.metadata.entity.DamScanJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DamScanJobMapper extends BaseMapper<DamScanJob> {
}
