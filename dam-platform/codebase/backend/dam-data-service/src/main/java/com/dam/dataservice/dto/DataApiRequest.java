package com.dam.dataservice.dto;

import lombok.Data;

/**
 * 数据 API 创建/更新请求
 * <p>
 * 与实体分离，避免调用方直接写入 id、审计字段与逻辑删除标记。
 * {@code upstreamConfig} 使用 Object 接收，既支持请求体中的 JSON 对象/数组，
 * 也支持直接传入 JSON 字符串，服务层统一序列化为 jsonb 文本。
 *
 * @author DAM Platform
 */
@Data
public class DataApiRequest {

    /** API 编码（创建必填；更新时不允许修改） */
    private String apiCode;

    /** API 名称 */
    private String apiName;

    /** API 访问路径 */
    private String path;

    /** HTTP 方法 */
    private String method;

    /** 发布状态（创建默认 DRAFT） */
    private String status;

    /** API 描述 */
    private String description;

    /** 上游类型 */
    private String upstreamType;

    /** 上游配置（JSON 对象/数组或 JSON 字符串） */
    private Object upstreamConfig;

    /** 创建人（可选，默认 system） */
    private String createdBy;

    /** 更新人（可选，默认 system） */
    private String updatedBy;
}
