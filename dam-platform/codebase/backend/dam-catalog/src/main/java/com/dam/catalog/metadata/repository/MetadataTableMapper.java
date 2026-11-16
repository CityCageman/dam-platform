package com.dam.catalog.metadata.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.metadata.entity.MetadataTable;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MetadataTableMapper extends BaseMapper<MetadataTable> {
}
