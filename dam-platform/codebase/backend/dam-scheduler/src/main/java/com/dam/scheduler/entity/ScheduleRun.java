package com.dam.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度任务运行记录。
 * <p>
 * 对应 dam_scheduler.schedule_run；触发接口只写入 PENDING 记录，
 * started_at/finished_at/error_message 由后续真实执行引擎回填。
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

    /** 记录创建时间 */
    private LocalDateTime createdAt;
}
