package com.dam.asset.shared.port;

import com.dam.asset.metric.entity.MetricDefinition;

import java.util.List;

/**
 * 指标只读查询端口。
 * <p>
 * 指标数据由 analytics-metric 服务独占，实现为
 * {@link com.dam.asset.shared.adapter.MetricLookupHttpAdapter}（HTTP 调用 analytics-metric），
 * Knowledge/Report 不再直接依赖 Metric Mapper 或 dam_dan 表。
 */
public interface MetricLookupPort {

    /** 按 ID 查询指标，不存在返回 null。 */
    MetricDefinition findById(Long id);

    /** 查询所有未删除指标，最多 limit 条。 */
    List<MetricDefinition> listActive(int limit);

    /** 查询指定数据源下启用指标。 */
    List<MetricDefinition> listActiveByDataSource(Long dataSourceId);
}
