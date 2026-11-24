package com.dam.analytics.metric.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dam.common.config.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 手工填报指标值（按期间 + 维度唯一）。
 * <p>
 * 表 {@code dam_metric.metric_value} 归 dam-metric 服务独占。逻辑删除同样使用
 * {@code deleted_at}，未删除记录由部分唯一索引 {@code (metric_id, period, dimension_key)}
 * 保证同一期间同一维度只有一条值。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "metric_value", schema = "dam_metric")
public class MetricValue {

    /** 主键（数据库 bigserial 自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属指标 ID */
    private Long metricId;

    /** 期间，如 2026-10 */
    private String period;

    /** 维度键，无维度时为空串 */
    private String dimensionKey;

    /** 维度取值（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String dimensionValues;

    /** 指标值 */
    private BigDecimal metricValue;

    /** 备注 */
    private String note;

    /** 状态：0-停用，1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 更新人 */
    private String updatedBy;

    /** 逻辑删除时间，非空表示已删除 */
    private LocalDateTime deletedAt;
}
