package com.dam.metric.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dam.common.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指标定义：MANUAL-手工填报，COMPUTED-系统计算。
 * <p>
 * 表 {@code dam_metric.metric_definition} 归 dam-metric 服务独占，catalog 不再直接读写；
 * catalog 只能通过本服务 {@code /api/metrics/internal/**} 只读接口获取指标。
 * <p>
 * 逻辑删除沿用 {@code deleted_at IS NULL} 表示有效记录：表上指标编码唯一索引是
 * {@code WHERE deleted_at IS NULL} 的部分唯一索引，若改用 MyBatis-Plus 的 {@code @TableLogic}
 * 会改变删除语义并与历史数据不兼容，因此查询处统一显式过滤 {@code deleted_at}。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "metric_definition", schema = "dam_metric")
public class MetricDefinition {

    /** 主键（数据库 bigserial 自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 指标编码，未删除记录内唯一 */
    private String metricCode;

    /** 指标名称 */
    private String metricName;

    /** 指标类型：MANUAL-手工填报，COMPUTED-系统计算 */
    private String metricType;

    /** 业务域 */
    private String businessDomain;

    /** 归属部门，用于数据范围过滤 */
    private String department;

    /** 业务定义 */
    private String definition;

    /** 计算公式 */
    private String formula;

    /** 单位 */
    private String unit;

    /** 数据源 ID，SQL 生成时由 catalog 校验数据源归属 */
    private Long dataSourceId;

    /** 来源表（JSONB 数组，使用 JsonbTypeHandler 读写） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String sourceTables;

    /** AI 生成或人工维护的指标 SQL */
    private String sqlExpression;

    /** 取数规则：粒度、去重、必用表、条件、主报表/下钻模板（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String reportSpec;

    /** 指标维度（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String dimensions;

    /** 时间粒度 */
    private String timeGrain;

    /** 负责人 */
    private String owner;

    /** 状态：0-停用，1-启用 */
    private Integer status;

    /** 版本号，更新或 AI 生成 SQL 后自增 */
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
