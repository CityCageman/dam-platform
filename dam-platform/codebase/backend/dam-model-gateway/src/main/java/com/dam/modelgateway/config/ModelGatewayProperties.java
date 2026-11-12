package com.dam.modelgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型网关配置（前缀 dam.model-gateway）
 * <p>
 * 对话智能体与编码智能体分开配置，密钥通过环境变量或 application-dev.yml 注入，
 * 仅在服务端使用，绝不下发前端。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dam.model-gateway")
public class ModelGatewayProperties {

    /** 统一智能体配置：平台只使用 code 模型（对话、SQL、注释、分类、报表全部共用） */
    private ModelConfigProperties code = new ModelConfigProperties();

    /**
     * 兼容旧的 agentType 参数：无论传 chat 还是 code，统一返回 code 配置。
     */
    public ModelConfigProperties of(String type) {
        return code;
    }
}
