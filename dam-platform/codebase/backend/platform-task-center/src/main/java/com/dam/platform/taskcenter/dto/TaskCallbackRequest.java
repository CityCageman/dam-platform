package com.dam.platform.taskcenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务回调开始请求。
 * <p>
 * 集成、调度、开发等外部执行器在开始运行时调用回调接口，以
 * {@code sourceService + sourceTaskCode + sourceRunId} 作为幂等键登记运行实例。
 * {@code payload} 支持直接传 JSON 对象/数组，也支持传 JSON 字符串。
 * <p>
 * 任务定义默认自动登记：先按 {@code sourceService + sourceTaskCode} 查找，
 * 找不到时自动创建 ENABLED 定义并继续登记实例；显式传
 * {@code autoCreateDefinition=false} 可恢复“找不到定义即返回 404”的行为。
 * 详见 {@code TaskCallbackService#start} 的说明。
 *
 * @author DAM Platform
 */
@Data
public class TaskCallbackRequest {

    /** 来源服务，如 dam-integration、dam-scheduler */
    @NotBlank(message = "sourceService 不能为空")
    @Size(max = 64, message = "sourceService 长度不能超过 64")
    private String sourceService;

    /** 来源任务编码，用于匹配任务定义或生成自动登记的 task_code */
    @NotBlank(message = "sourceTaskCode 不能为空")
    @Size(max = 64, message = "sourceTaskCode 长度不能超过 64")
    private String sourceTaskCode;

    /** 来源运行ID，由来源服务生成，同一任务内唯一 */
    @NotBlank(message = "sourceRunId 不能为空")
    @Size(max = 128, message = "sourceRunId 长度不能超过 128")
    private String sourceRunId;

    /** 任务类型：INTEGRATION/SCHEDULER/DEV/QUALITY/AI */
    @NotBlank(message = "taskType 不能为空")
    @Size(max = 32, message = "taskType 长度不能超过 32")
    private String taskType;

    /** 本次运行标题 */
    @NotBlank(message = "title 不能为空")
    @Size(max = 200, message = "title 长度不能超过 200")
    private String title;

    /** 运行参数（JSON 对象/数组，或合法 JSON 字符串；可选） */
    private Object payload;

    /** 可选：直接指定任务定义ID；不传时按 sourceService + sourceTaskCode 查找 */
    private Long definitionId;

    /**
     * 定义不存在时是否自动登记任务定义，默认 true。
     * <p>
     * 自动登记使用 {@code sourceService + sourceTaskCode} 生成稳定 taskCode，
     * 状态为 ENABLED；显式传 false 时定义不存在将返回 404。
     */
    private boolean autoCreateDefinition = true;
}
