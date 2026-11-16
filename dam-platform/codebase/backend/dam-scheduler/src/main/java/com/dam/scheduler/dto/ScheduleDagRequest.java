package com.dam.scheduler.dto;

import lombok.Data;
import lombok.ToString;
import tools.jackson.databind.JsonNode;

/**
 * 创建/更新 DAG 请求。
 * <p>
 * {@code definition} 接受 JSON 对象或 JSON 字符串，服务层统一校验并规范化为
 * jsonb 文本；POST/PUT 可使用原始明文 datasource.password。
 *
 * @author DAM Platform
 */
@Data
public class ScheduleDagRequest {

    /** DAG 编码；创建时必填，更新时必须与路径一致或留空 */
    private String dagCode;

    /** DAG 名称 */
    private String dagName;

    /** DAG 定义 JSON（包含原始密码，禁止写入日志） */
    @ToString.Exclude
    private JsonNode definition;

    /** 描述 */
    private String description;

    /** 状态：ENABLED/DISABLED，创建时缺省 ENABLED */
    private String status;
}
