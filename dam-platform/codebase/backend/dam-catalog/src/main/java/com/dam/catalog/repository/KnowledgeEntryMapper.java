package com.dam.catalog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.entity.KnowledgeEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeEntryMapper extends BaseMapper<KnowledgeEntry> {
}
