package com.dam.platform.taskcenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务定义创建/更新请求。
 * <p>
 * 创建接口启用 Bean Validation，校验 taskCode/taskName/taskType/sourceService；
 * 更新接口允许部分字段更新，taskCode 若传入必须与原值一致，否则返回 400。
 * config 支持直接传 JSON 对象/数组，也支持传 JSON 字符串。
 *
 * @author DAM Platform
 */
@Data
public class TaskDefinitionRequest {

    /** 任务编码（全局唯一；创建后不可修改） */
    @NotBlank(message = "taskCode 不能为空")
    @Size(max = 64, message = "taskCode 长度不能超过 64")
    private String taskCode;

    /** 任务名称 */
    @NotBlank(message = "taskName 不能为空")
    @Size(max = 200, message = "taskName 长度不能超过 200")
    private String taskName;

    /** 任务类型：INTEGRATION/SCHEDULER/DEV/QUALITY/AI */
    @NotBlank(message = "taskType 不能为空")
    @Size(max = 32, message = "taskType 长度不能超过 32")
    private String taskType;

    /** 来源服务 */
    @NotBlank(message = "sourceService 不能为空")
    @Size(max = 64, message = "sourceService 长度不能超过 64")
    private String sourceService;

    /** 状态：DRAFT/ENABLED/DISABLED，创建时缺省 DRAFT */
    @Size(max = 16, message = "status 长度不能超过 16")
    private String status;

    /** 任务配置（JSON 对象/数组或 JSON 字符串） */
    private Object config;

    /** 任务描述 */
    @Size(max = 2000, message = "description 长度不能超过 2000")
    private String description;
}
