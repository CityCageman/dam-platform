package com.dam.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智能体连接配置（前缀 dam.agent）
 * <p>
 * 对话智能体与编码智能体分开配置，密钥通过环境变量或 application-dev.yml 注入，
 * 仅在服务端使用，绝不下发前端。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dam.agent")
public class AgentProperties {

    /** 对话智能体配置（通用问答） */
    private AgentConfigProperties chat = new AgentConfigProperties();

    /** 编码智能体配置（代码 / SQL 生成） */
    private AgentConfigProperties code = new AgentConfigProperties();

    /**
     * 按类型选择配置：{@code code} 取编码智能体，其余（含空）一律取对话智能体。
     */
    public AgentConfigProperties of(String type) {
        return "code".equalsIgnoreCase(type) ? code : chat;
    }
}
