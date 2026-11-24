package com.dam.asset.metadata.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.asset.metadata.entity.MetadataSchema;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MetadataSchemaMapper extends BaseMapper<MetadataSchema> {
}
