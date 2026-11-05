package com.dam.catalog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.entity.KnowledgeCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeCategoryMapper extends BaseMapper<KnowledgeCategory> {
}
