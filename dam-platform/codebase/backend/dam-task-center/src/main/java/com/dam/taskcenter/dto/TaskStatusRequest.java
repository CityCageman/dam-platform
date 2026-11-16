package com.dam.taskcenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务实例状态流转请求。
 *
 * @author DAM Platform
 */
@Data
public class TaskStatusRequest {

    /** 目标状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED */
    @NotBlank(message = "status 不能为空")
    @Size(max = 16, message = "status 长度不能超过 16")
    private String status;

    /** 失败原因（仅 FAILED 状态允许设置） */
    @Size(max = 2000, message = "errorMessage 长度不能超过 2000")
    private String errorMessage;
}
