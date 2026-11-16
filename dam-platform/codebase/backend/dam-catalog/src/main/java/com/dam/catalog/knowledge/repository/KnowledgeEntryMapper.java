package com.dam.catalog.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.knowledge.entity.KnowledgeEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeEntryMapper extends BaseMapper<KnowledgeEntry> {
}
