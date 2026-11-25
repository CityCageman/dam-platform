package com.dam.platform.taskcenter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务实例。
 * <p>
 * 对应 {@code dam_dpm.task_instance}：一次任务触发对应一条实例记录。
 * 触发接口只写入 PENDING 实例，不伪造执行结果；started_at/finished_at/
 * error_message 由执行器或状态回填接口维护。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "task_instance", schema = "dam_dpm")
public class TaskInstance {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务定义ID */
    private Long definitionId;

    /** 任务编码（触发时从任务定义快照，避免定义修改影响历史实例） */
    private String taskCode;

    /** 同一任务定义内的运行序号（从 1 开始） */
    private Integer runNo;

    /** 运行状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED */
    private String status;

    /** 触发方式：MANUAL/SCHEDULE/API */
    private String triggerType;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 失败原因 */
    private String errorMessage;

    /** 实例创建时间 */
    private LocalDateTime createdAt;

    /** 回调来源服务 */
    private String sourceService;

    /** 回调来源任务编码 */
    private String sourceTaskCode;

    /** 回调来源运行ID */
    private String sourceRunId;

    /** 回调运行标题 */
    private String title;

    /** 回调运行参数 JSON 文本 */
    private String payload;
}
