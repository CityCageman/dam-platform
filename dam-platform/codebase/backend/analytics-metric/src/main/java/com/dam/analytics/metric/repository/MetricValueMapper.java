package com.dam.analytics.metric.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.analytics.metric.entity.MetricValue;
import org.apache.ibatis.annotations.Mapper;

/**
 * 指标值 Mapper，仅 analytics-metric 服务使用。
 * <p>
 * 指标值表归本服务独占；catalog 只通过内部只读接口按指标/数据源查询指标定义，
 * 不直接访问 {@code dam_metric.metric_value}。
 *
 * @author DAM Platform
 */
@Mapper
public interface MetricValueMapper extends BaseMapper<MetricValue> {
}
