package com.dam.catalog.shared.port;

import com.dam.catalog.metric.entity.MetricDefinition;

import java.util.List;

/**
 * 指标只读查询端口。
 * 当前由本地适配器实现；拆分 dam-metric 服务后可替换为 HTTP 适配器，
 * Knowledge/Report 不再直接依赖 Metric Mapper。
 */
public interface MetricLookupPort {

    /** 按 ID 查询指标，不存在返回 null。 */
    MetricDefinition findById(Long id);

    /** 查询所有未删除指标，最多 limit 条。 */
    List<MetricDefinition> listActive(int limit);

    /** 查询指定数据源下启用指标。 */
    List<MetricDefinition> listActiveByDataSource(Long dataSourceId);
}
