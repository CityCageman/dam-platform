package com.dam.platform.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度任务运行记录。
 * <p>
 * 对应 dam_scheduler.schedule_run；手动触发与 Cron 自动派发先写入 PENDING 记录，
 * 提交后由 ScheduleRunExecutor 流转为 RUNNING 并回填 finished_at/error_message。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "schedule_run", schema = "dam_scheduler")
public class ScheduleRun {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属调度任务ID */
    private Long jobId;

    /** 运行状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED */
    private String runStatus;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 失败原因 */
    private String errorMessage;

    /**
     * DolphinScheduler 流程实例ID。
     * <p>
     * execution_engine=DOLPHIN 的运行在触发 DS 成功后回填；非空即表示该 run 由 DS 执行、
     * 由状态轮询回写终态。LOCAL 运行始终为 NULL。
     */
    private Long dolphinProcessInstanceId;

    /**
     * TaskCenter 运行实例ID。
     * <p>
     * DS 触发阶段登记 TaskCenter 后保存，供后台轮询在 DS 终态时回写 finish；
     * LOCAL 运行在同一执行线程内完成回调，不需要该字段。
     */
    private Long taskCenterInstanceId;

    /** 记录创建时间 */
    private LocalDateTime createdAt;
}
