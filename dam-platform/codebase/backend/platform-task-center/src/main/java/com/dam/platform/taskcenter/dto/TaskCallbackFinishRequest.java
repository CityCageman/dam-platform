package com.dam.platform.taskcenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务回调结束请求。
 * <p>
 * 仅允许把实例更新为终态 SUCCESS/FAILED/CANCELLED；rowsRead/rowsWritten 可选。
 * 由于 task_instance 没有计数字段，计数会以结构化日志写入 task_log，
 * 并在回调响应中回显，不伪造实例列。
 *
 * @author DAM Platform
 */
@Data
public class TaskCallbackFinishRequest {

    /** 目标终态：SUCCESS/FAILED/CANCELLED */
    @NotBlank(message = "status 不能为空")
    @Size(max = 16, message = "status 长度不能超过 16")
    private String status;

    /** 读取行数（可选，非负） */
    @PositiveOrZero(message = "rowsRead 不能为负数")
    private Long rowsRead;

    /** 写入行数（可选，非负） */
    @PositiveOrZero(message = "rowsWritten 不能为负数")
    private Long rowsWritten;

    /** 失败/取消原因（可选；SUCCESS 不允许设置） */
    @Size(max = 2000, message = "errorMessage 长度不能超过 2000")
    private String errorMessage;
}
