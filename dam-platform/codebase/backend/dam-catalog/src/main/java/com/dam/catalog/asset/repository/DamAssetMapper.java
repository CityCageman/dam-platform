package com.dam.catalog.asset.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.asset.entity.DamAsset;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DamAssetMapper extends BaseMapper<DamAsset> {
}
