package com.dam.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * DAG 响应。
 * <p>
 * {@code definition} 中所有节点的 {@code datasource.password} 均已替换为
 * {@code ***}，不会返回原始密码。
 *
 * @author DAM Platform
 */
@Data
@AllArgsConstructor
public class ScheduleDagView {

    /** DAG 编码 */
    private String dagCode;

    /** DAG 名称 */
    private String dagName;

    /** 脱敏后的 DAG 定义 */
    private JsonNode definition;

    /** 描述 */
    private String description;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
