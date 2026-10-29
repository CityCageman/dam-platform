package com.dam.agent.config;

import lombok.Data;

/**
 * 单个智能体的连接配置（对话智能体 / 编码智能体共用结构）
 * <p>
 * 密钥仅在服务端使用，绝不下发前端。
 */
@Data
public class AgentConfigProperties {

    /** 智能体服务地址（完整到 /v1/chat/completions） */
    private String baseUrl;

    /** 认证密钥（Authorization: Bearer <apiKey>） */
    private String apiKey;

    /** 默认模型 */
    private String model;

    /** 总体超时（秒），流式请求需要更长 */
    private long timeoutSeconds = 120;

    /** 连接超时（秒） */
    private long connectTimeoutSeconds = 10;
}
