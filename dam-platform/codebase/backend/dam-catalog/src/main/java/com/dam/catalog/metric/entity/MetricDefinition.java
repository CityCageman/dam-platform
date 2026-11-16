package com.dam.catalog.metric.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标定义跨服务 DTO：MANUAL-手工填报，COMPUTED-系统计算。
 * <p>
 * 指标数据已由 dam-metric 服务独占（dam_metric.metric_definition），
 * catalog 仅通过 {@code MetricLookupPort} 的 HTTP 适配器读取，
 * 因此本类不再携带 MyBatis-Plus 表映射注解。
 */
@Data
public class MetricDefinition {
    private Long id;

    private String metricCode;
    private String metricName;
    private String metricType;
    private String businessDomain;
    private String definition;
    private String formula;
    private String unit;
    private Long dataSourceId;

    /** JSON 字符串：来源表 */
    private String sourceTables;

    private String sqlExpression;

    /** JSON 字符串：取数规则（粒度、去重、必用表、条件、主报表/下钻模板） */
    private String reportSpec;

    /** JSON 字符串：维度 */
    private String dimensions;

    private String timeGrain;
    private String owner;
    private Integer status;
    private Integer version;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
