package com.dam.catalog.asset.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.asset.entity.DamFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DamFavoriteMapper extends BaseMapper<DamFavorite> {
}
