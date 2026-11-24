package com.dam.platform.scheduler.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dam.common.config.JsonbTypeHandler;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 调度 DAG 定义。
 * <p>
 * 对应 dam_scheduler.schedule_dag。{@code definition} 为 PostgreSQL jsonb，
 * 保存原始 JSON（包含 datasource.password），仅供执行器读取；对外响应由服务层脱敏。
 * 当前仅支持节点顺序执行的 SQL DAG。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "schedule_dag", schema = "dam_scheduler", autoResultMap = true)
public class ScheduleDag {

    /** DAG 编码，主键 */
    @TableId(value = "dag_code", type = IdType.INPUT)
    private String dagCode;

    /** DAG 名称 */
    private String dagName;

    /**
     * DAG 定义（PostgreSQL jsonb 原文）。
     * <p>
     * 包含节点 SQL 与 datasource 凭据，禁止写入日志；toString 中排除该字段。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    @ToString.Exclude
    private String definition;

    /** 描述 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
