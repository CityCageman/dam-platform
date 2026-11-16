package com.dam.metric.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.metric.entity.MetricDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 指标定义 Mapper，仅 dam-metric 服务使用。
 * <p>
 * 指标定义表归本服务独占；catalog 不得再声明同名 Mapper 或直接访问
 * {@code dam_metric.metric_definition}。
 *
 * @author DAM Platform
 */
@Mapper
public interface MetricDefinitionMapper extends BaseMapper<MetricDefinition> {
}
