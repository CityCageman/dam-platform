package com.dam.asset.metadata.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.asset.metadata.entity.DamScanJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DamScanJobMapper extends BaseMapper<DamScanJob> {
}
