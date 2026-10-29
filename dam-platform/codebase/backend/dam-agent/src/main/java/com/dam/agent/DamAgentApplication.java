package com.dam.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能体服务启动类
 * <p>
 * 提供大模型智能问答能力：独立聊天窗口（SSE 流式）与业务嵌入（一次性完成）。
 * 复用 dam-common 的统一响应、异常处理；不连接数据库（已排除数据源自动装配）。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.agent", "com.dam.common"})
public class DamAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DamAgentApplication.class, args);
    }
}
