package com.dam.asset.metadata.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.asset.metadata.entity.MetadataTable;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MetadataTableMapper extends BaseMapper<MetadataTable> {
}
