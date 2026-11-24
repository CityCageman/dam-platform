package com.dam.platform.taskcenter.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dam.platform.common.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务定义。
 * <p>
 * 对应 {@code dam_task.task_definition}：统一登记集成、调度、开发、质量与 AI 任务的
 * 元数据。{@code config} 为 PostgreSQL jsonb，使用 platform-common 的
 * {@link JsonbTypeHandler} 读写；{@code deleted_at} 使用 MyBatis-Plus 逻辑删除，
 * 未删除为 NULL，删除时置 now()。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "task_definition", schema = "dam_task", autoResultMap = true)
public class TaskDefinition {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编码（全局唯一，创建后不允许修改） */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 任务类型：INTEGRATION/SCHEDULER/DEV/QUALITY/AI */
    private String taskType;

    /** 来源服务（如 access-integration、platform-scheduler） */
    private String sourceService;

    /** 状态：DRAFT/ENABLED/DISABLED */
    private String status;

    /** 任务配置（PostgreSQL jsonb） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String config;

    /** 任务描述 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 更新人 */
    private String updatedBy;

    /** 逻辑删除时间（NULL 表示未删除） */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
