package com.dam.catalog.asset.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.asset.entity.AssetUsageLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetUsageLogMapper extends BaseMapper<AssetUsageLog> {
}
