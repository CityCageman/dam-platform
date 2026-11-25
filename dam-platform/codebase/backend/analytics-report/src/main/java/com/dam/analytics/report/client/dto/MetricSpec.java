package com.dam.analytics.report.client.dto;

import lombok.Data;

/**
 * 指标定义（analytics-metric 通过内部 AI 工具接口传入的指标 DTO）。
 * <p>
 * 报表模块不再依赖 asset-catalog 的 MetricDefinition 实体，仅保留生成 SQL 所需的业务字段。
 *
 * @author DAM Platform
 */
@Data
public class MetricSpec {

    private Long id;
    private String metricCode;
    private String metricName;
    private String metricType;
    private String businessDomain;
    private String definition;
    private String formula;
    private String unit;
    private Long dataSourceId;
    private String sourceTables;
    private String sqlExpression;
    private String reportSpec;
    private String dimensions;
    private String timeGrain;
    private String owner;
    private Integer status;
    private Integer version;
}
