package com.dam.platform.scheduler.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度任务定义。
 * <p>
 * 对应 dam_dpm.schedule_job；仅存储调度元数据，不包含 DAG 执行逻辑。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "schedule_job", schema = "dam_dpm")
public class ScheduleJob {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编码，全局唯一，创建后不允许修改 */
    private String jobCode;

    /** 任务名称 */
    private String jobName;

    /** Cron 表达式（Spring 六段式） */
    private String cronExpression;

    /** 关联 DAG 编码，可为空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String dagCode;

    /**
     * 执行引擎：LOCAL/DOLPHIN。
     * <p>
     * LOCAL 走本地顺序 SQL DAG 执行器；DOLPHIN 触发 DolphinScheduler 流程实例并轮询状态。
     * 历史数据由迁移脚本回填为 LOCAL。
     */
    private String executionEngine;

    /** DolphinScheduler 流程定义编码，executionEngine=DOLPHIN 时必填 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long dolphinProcessDefinitionCode;

    /** DolphinScheduler 流程定义版本，可为空表示由 DS 侧决定 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer dolphinProcessDefinitionVersion;

    /** 状态：DRAFT/ENABLED/DISABLED */
    private String status;

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

    /**
     * 最近一次自动派发时间。
     * <p>
     * 仅由自动派发扫描在成功插入运行记录后更新；手动触发不修改该字段。
     * 为空时以 createdAt 作为计算 Cron 下一次触发点的基准。
     * <p>
     * 使用 updateStrategy=NEVER，避免普通任务编辑通过 updateById 把并发派发写入的
     * 新值回退成旧值，造成重复派发；该列只允许自动派发 Mapper 的专用 SQL 更新。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime lastDispatchedAt;

    /**
     * 删除时间。
     * <p>
     * 逻辑删除：未删除为 NULL，删除时置 now()；deleted_at 为 TIMESTAMP 列，
     * 不能使用默认的 0/1 删除值比较。
     */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
