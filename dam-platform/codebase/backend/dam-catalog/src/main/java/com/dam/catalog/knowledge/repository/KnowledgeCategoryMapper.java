package com.dam.catalog.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.knowledge.entity.KnowledgeCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeCategoryMapper extends BaseMapper<KnowledgeCategory> {
}
