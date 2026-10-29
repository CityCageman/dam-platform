package com.dam.agent.dto;

import lombok.Data;

/**
 * 一次性完成响应（业务嵌入场景）
 */
@Data
public class ChatCompletionResponse {

    /** 回复内容 */
    private String content;

    /** 实际使用的模型 */
    private String model;

    /** 结束原因 */
    private String finishReason;

    /** 提示词 token 数 */
    private Integer promptTokens;

    /** 补全 token 数 */
    private Integer completionTokens;

    /** 总 token 数 */
    private Integer totalTokens;
}
