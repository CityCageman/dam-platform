package com.dam.catalog.metric.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.metric.entity.MetricValue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MetricValueMapper extends BaseMapper<MetricValue> {
}
