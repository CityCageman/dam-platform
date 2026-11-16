package com.dam.taskcenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务日志追加请求。
 *
 * @author DAM Platform
 */
@Data
public class TaskLogRequest {

    /** 日志级别：TRACE/DEBUG/INFO/WARN/ERROR，缺省 INFO */
    @Size(max = 16, message = "level 长度不能超过 16")
    private String level;

    /** 日志内容 */
    @NotBlank(message = "message 不能为空")
    @Size(max = 10000, message = "message 长度不能超过 10000")
    private String message;
}
