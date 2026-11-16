package com.dam.taskcenter.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务回调响应。
 * <p>
 * 统一返回任务实例的当前快照。sourceService/sourceTaskCode/sourceRunId/title/payload
 * 依赖 task_instance 中不存在的来源字段，当前 start 接口未启用，因此这些字段为 null；
 * 待数据库迁移后可直接填充，不需要再改 API 契约。
 * <p>
 * created/reused 仅用于 start 幂等语义：created=true 表示新建，reused=true 表示命中
 * 已有实例。当前 start 返回 501，不会产生这两种成功响应。
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

    /** 来源服务（当前无存储字段，固定为 null） */
    private String sourceService;

    /** 来源任务编码（当前无存储字段，固定为 null） */
    private String sourceTaskCode;

    /** 来源运行ID（当前无存储字段，固定为 null） */
    private String sourceRunId;

    /** 任务类型（INTEGRATION/SCHEDULER/DEV/QUALITY/AI） */
    private String taskType;

    /** 运行标题（当前无存储字段，固定为 null） */
    private String title;

    /** 运行参数（当前无存储字段，固定为 null） */
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

    /** 是否为本次 start 新建（当前 start 不可用，恒为 false） */
    private boolean created;

    /** 是否命中已有实例（当前 start 不可用，恒为 false） */
    private boolean reused;

    /** 结果说明 */
    private String message;
}
