package com.dam.catalog.metadata.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.metadata.entity.MetadataManagement;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MetadataManagementMapper extends BaseMapper<MetadataManagement> {
}
