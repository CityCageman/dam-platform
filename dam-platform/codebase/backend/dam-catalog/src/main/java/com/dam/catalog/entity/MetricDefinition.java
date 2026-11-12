package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.catalog.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标定义：MANUAL-手工填报，COMPUTED-系统计算
 */
@Data
@TableName(value = "metric_definition", schema = "dam_asset")
public class MetricDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String metricCode;
    private String metricName;
    private String metricType;
    private String businessDomain;
    private String definition;
    private String formula;
    private String unit;
    private Long dataSourceId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String sourceTables;

    private String sqlExpression;

    /** 取数规则：粒度、去重、必用表、条件、主报表/下钻模板 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String reportSpec;

    @TableField(typeHandler = JsonbTypeHandler.class)
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
