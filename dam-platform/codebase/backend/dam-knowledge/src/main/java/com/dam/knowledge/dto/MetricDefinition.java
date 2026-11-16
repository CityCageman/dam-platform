package com.dam.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标定义只读 DTO。
 * <p>
 * 与 dam-metric 的 {@code MetricDefinition} 返回结构保持一致，仅用于知识库展示指标口径、
 * 校验指标归属；本服务不持久化、不修改指标数据。字段与 dam-metric 内部接口
 * {@code GET /api/metrics/internal/active}、{@code GET /api/metrics/{id}} 的 data 对齐。
 *
 * @author DAM Platform
 */
@Data
public class MetricDefinition {

    /** 主键 */
    private Long id;

    /** 指标编码 */
    private String metricCode;

    /** 指标名称 */
    private String metricName;

    /** 指标类型：MANUAL-手工填报，COMPUTED-系统计算 */
    private String metricType;

    /** 业务域 */
    private String businessDomain;

    /** 业务定义 */
    private String definition;

    /** 计算公式 */
    private String formula;

    /** 单位 */
    private String unit;

    /** 数据源 ID */
    private Long dataSourceId;

    /** 来源表（JSONB 数组） */
    private String sourceTables;

    /** 指标 SQL */
    private String sqlExpression;

    /** 取数规则（JSONB） */
    private String reportSpec;

    /** 指标维度（JSONB） */
    private String dimensions;

    /** 时间粒度 */
    private String timeGrain;

    /** 负责人 */
    private String owner;

    /** 状态：0-停用，1-启用 */
    private Integer status;

    /** 版本号 */
    private Integer version;

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
