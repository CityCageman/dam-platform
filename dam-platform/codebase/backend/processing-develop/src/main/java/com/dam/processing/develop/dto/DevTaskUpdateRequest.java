package com.dam.processing.develop.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新数据开发任务请求
 * <p>
 * 为 null 的字段保持原值；taskCode 只用于校验是否被修改，不允许改变。
 * content 提供且与当前内容不同时，服务端会在同一事务内将 currentVersion 加 1
 * 并追加一条版本记录；content 相同则不产生新版本。datasourceConfig 变更只更新
 * 主表字段，不单独生成版本记录。
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

    /**
     * SQL 任务执行用 JDBC 数据源配置（可选）
     * <p>
     * 支持 JSON 对象或 JSON 字符串；为 null 时保持原值，传空字符串表示清空配置。
     * 根字段 {@code password} 落库前加密为 {@code ENC1:} 前缀密文；回传已有的密文
     * 不会重复加密，空值保持不变。
     * 该字段变更不会递增任务版本号（仅 content 变更升版本），仅 SQL 任务执行时读取。
     */
    private Object datasourceConfig;

    /** 任务脚本内容（可选；变更时生成新版本） */
    private String content;

    /** 任务描述（可选） */
    private String description;

    /** 本次内容变更说明（可选，仅在 content 变更时写入版本记录） */
    @Size(max = 500, message = "changeNote 长度不能超过 500")
    private String changeNote;
}
