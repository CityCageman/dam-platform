package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.catalog.config.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 手工填报指标值
 */
@Data
@TableName(value = "metric_value", schema = "dam_asset")
public class MetricValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long metricId;
    private String period;
    private String dimensionKey;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String dimensionValues;

    private BigDecimal metricValue;
    private String note;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
