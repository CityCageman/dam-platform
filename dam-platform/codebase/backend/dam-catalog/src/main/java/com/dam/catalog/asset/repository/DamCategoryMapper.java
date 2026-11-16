package com.dam.catalog.asset.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.asset.entity.DamCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DamCategoryMapper extends BaseMapper<DamCategory> {
}
