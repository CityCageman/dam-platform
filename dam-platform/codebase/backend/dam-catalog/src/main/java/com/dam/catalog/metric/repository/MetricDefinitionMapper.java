package com.dam.catalog.metric.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.metric.entity.MetricDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MetricDefinitionMapper extends BaseMapper<MetricDefinition> {
}
