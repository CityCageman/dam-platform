package com.dam.taskcenter.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务实例取消请求。
 * <p>
 * 请求体可省略；省略或 reason 为空白时使用默认原因“人工取消”。
 * 兼容早期草稿字段 errorMessage（通过 {@link JsonAlias}）。
 *
 * @author DAM Platform
 */
@Data
public class TaskInstanceCancelRequest {

    /** 取消原因（可选，默认“人工取消”） */
    @JsonAlias("errorMessage")
    @Size(max = 2000, message = "reason 长度不能超过 2000")
    private String reason;
}
