package com.dam.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 对话请求
 */
@Data
public class ChatRequest {

    /** 对话消息列表 */
    @NotEmpty(message = "messages不能为空")
    @Valid
    private List<ChatMessage> messages;

    /** 智能体类型：chat=对话（默认），code=编码 */
    private String agentType = "chat";

    /** 可选：覆盖默认模型 */
    private String model;

    /** 可选：采样温度 */
    private Double temperature;

    /** 可选：最大输出 token 数 */
    private Integer maxTokens;
}
