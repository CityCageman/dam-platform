package com.dam.asset.catalog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.asset.catalog.entity.DamAsset;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DamAssetMapper extends BaseMapper<DamAsset> {
}
