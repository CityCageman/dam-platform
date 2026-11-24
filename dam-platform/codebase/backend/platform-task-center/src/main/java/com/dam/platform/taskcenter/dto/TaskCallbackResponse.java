package com.dam.platform.taskcenter.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务回调响应。
 * <p>
 * 统一返回任务实例的当前快照。sourceService/sourceTaskCode/sourceRunId/title/payload
 * 由 start 在登记实例时持久化并原样回显；definitionId/taskCode 指向实际使用的
 * 任务定义（自动登记时为新建或已存在的定义）。
 * <p>
 * created/reused 仅用于 start 幂等语义：created=true 表示本次新建实例，
 * reused=true 表示命中已有实例；finish/get 等其他接口均为 false。
 *
 * @author DAM Platform
 */
@Data
public class TaskCallbackResponse {

    /** 任务实例ID */
    private Long instanceId;

    /** 任务定义ID */
    private Long definitionId;

    /** 任务编码（取自任务定义；定义已删除时为实例快照） */
    private String taskCode;

    /** task_instance.task_code 的实际存储值（用于排查编码/历史数据） */
    private String instanceTaskCode;

    /** 来源服务 */
    private String sourceService;

    /** 来源任务编码 */
    private String sourceTaskCode;

    /** 来源运行ID */
    private String sourceRunId;

    /** 任务类型（INTEGRATION/SCHEDULER/DEV/QUALITY/AI） */
    private String taskType;

    /** 运行标题 */
    private String title;

    /** 运行参数（JSON 文本） */
    private Object payload;

    /** 实例状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED */
    private String status;

    /** 触发方式：MANUAL/SCHEDULE/API */
    private String triggerType;

    /** 同一任务定义内的运行序号 */
    private Integer runNo;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 失败/取消原因 */
    private String errorMessage;

    /** 读取行数（来自回调结束日志；未上报时为 null） */
    private Long rowsRead;

    /** 写入行数（来自回调结束日志；未上报时为 null） */
    private Long rowsWritten;

    /** 实例创建时间 */
    private LocalDateTime createdAt;

    /** 是否为本次 start 新建实例 */
    private boolean created;

    /** 是否命中已有实例 */
    private boolean reused;

    /** 结果说明 */
    private String message;
}
