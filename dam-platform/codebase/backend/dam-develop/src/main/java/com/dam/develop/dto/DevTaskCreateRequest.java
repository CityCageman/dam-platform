package com.dam.develop.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建数据开发任务请求
 * <p>
 * 只暴露任务定义字段，id、状态、版本号与审计字段由服务端维护。创建后的任务
 * 固定为 DRAFT 状态且版本号为 1；发布/下线通过独立接口完成。
 *
 * @author DAM Platform
 */
@Data
public class DevTaskCreateRequest {

    /** 任务编码（必填，全局唯一，创建后不可修改） */
    @NotBlank(message = "taskCode 不能为空")
    @Size(max = 64, message = "taskCode 长度不能超过 64")
    @JsonAlias("code")
    private String taskCode;

    /** 任务名称（必填） */
    @NotBlank(message = "taskName 不能为空")
    @Size(max = 128, message = "taskName 长度不能超过 128")
    @JsonAlias("name")
    private String taskName;

    /** 任务类型（必填：SQL/PYTHON/SHELL） */
    @NotBlank(message = "taskType 不能为空")
    @Size(max = 16, message = "taskType 长度不能超过 16")
    @JsonAlias("type")
    private String taskType;

    /** 关联数据源 ID（可选） */
    private Long datasourceId;

    /**
     * SQL 任务执行用 JDBC 数据源配置（可选）
     * <p>
     * 支持传入 JSON 对象，也支持传入 JSON 字符串；服务端统一规范化后以 JSONB 保存。
     * 仅 SQL 任务会在执行时读取该配置，其他任务类型不会使用。
     */
    private Object datasourceConfig;

    /** 任务脚本内容（必填） */
    @NotBlank(message = "content 不能为空")
    private String content;

    /** 任务描述（可选） */
    private String description;

    /** 初始版本变更说明（可选，默认“初始版本”） */
    @Size(max = 500, message = "changeNote 长度不能超过 500")
    private String changeNote;
}
