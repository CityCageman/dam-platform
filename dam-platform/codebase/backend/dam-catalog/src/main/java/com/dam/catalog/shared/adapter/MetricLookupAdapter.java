package com.dam.catalog.shared.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.metric.entity.MetricDefinition;
import com.dam.catalog.metric.repository.MetricDefinitionMapper;
import com.dam.catalog.shared.port.MetricLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** MetricLookupPort 的本地数据库适配器，待 dam-metric 服务拆分后替换。 */
@Component
@RequiredArgsConstructor
public class MetricLookupAdapter implements MetricLookupPort {

    private final MetricDefinitionMapper metricMapper;

    @Override
    public MetricDefinition findById(Long id) {
        return id == null ? null : metricMapper.selectById(id);
    }

    @Override
    public List<MetricDefinition> listActive(int limit) {
        int size = Math.max(1, Math.min(limit, 500));
        return metricMapper.selectList(new LambdaQueryWrapper<MetricDefinition>()
                .isNull(MetricDefinition::getDeletedAt)
                .last("LIMIT " + size));
    }

    @Override
    public List<MetricDefinition> listActiveByDataSource(Long dataSourceId) {
        return metricMapper.selectList(new LambdaQueryWrapper<MetricDefinition>()
                .eq(MetricDefinition::getDataSourceId, dataSourceId)
                .eq(MetricDefinition::getStatus, 1)
                .isNull(MetricDefinition::getDeletedAt)
                .last("LIMIT 50"));
    }
}
