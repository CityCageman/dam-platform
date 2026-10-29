package com.dam.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 角色：system / user / assistant */
    @NotBlank(message = "role不能为空")
    private String role;

    /** 消息内容 */
    @NotBlank(message = "content不能为空")
    private String content;
}
