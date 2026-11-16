package com.dam.develop.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新数据开发任务请求
 * <p>
 * 为 null 的字段保持原值；taskCode 只用于校验是否被修改，不允许改变。
 * content 提供且与当前内容不同时，服务端会在同一事务内将 currentVersion 加 1
 * 并追加一条版本记录；content 相同则不产生新版本。
 *
 * @author DAM Platform
 */
@Data
public class DevTaskUpdateRequest {

    /** 任务编码（可选，仅用于校验；与现有编码不一致时返回 400） */
    @Size(max = 64, message = "taskCode 长度不能超过 64")
    @JsonAlias("code")
    private String taskCode;

    /** 任务名称（可选） */
    @Size(max = 128, message = "taskName 长度不能超过 128")
    @JsonAlias("name")
    private String taskName;

    /** 任务类型（可选：SQL/PYTHON/SHELL） */
    @Size(max = 16, message = "taskType 长度不能超过 16")
    @JsonAlias("type")
    private String taskType;

    /** 关联数据源 ID（可选） */
    private Long datasourceId;

    /** 任务脚本内容（可选；变更时生成新版本） */
    private String content;

    /** 任务描述（可选） */
    private String description;

    /** 本次内容变更说明（可选，仅在 content 变更时写入版本记录） */
    @Size(max = 500, message = "changeNote 长度不能超过 500")
    private String changeNote;
}
