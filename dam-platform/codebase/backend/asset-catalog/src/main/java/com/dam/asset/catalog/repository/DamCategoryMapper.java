package com.dam.asset.catalog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.asset.catalog.entity.DamCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DamCategoryMapper extends BaseMapper<DamCategory> {
}
