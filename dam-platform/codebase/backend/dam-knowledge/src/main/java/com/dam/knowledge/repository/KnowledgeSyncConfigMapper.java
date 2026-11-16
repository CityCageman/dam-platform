package com.dam.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.knowledge.entity.KnowledgeSyncConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeSyncConfigMapper extends BaseMapper<KnowledgeSyncConfig> {
}
