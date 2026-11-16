package com.dam.scheduler.entity;

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
 * 对应 dam_scheduler.schedule_job；仅存储调度元数据，不包含 DAG 执行逻辑。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "schedule_job", schema = "dam_scheduler")
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
     * 删除时间。
     * <p>
     * 逻辑删除：未删除为 NULL，删除时置 now()；deleted_at 为 TIMESTAMP 列，
     * 不能使用默认的 0/1 删除值比较。
     */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
