package com.dam.modelgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 模型网关服务启动类
 * <p>
 * 统一封装模型供应商调用：流式输出与一次性完成，业务服务不直接持有模型密钥。
 * 复用 dam-common 的统一响应、异常处理；不连接数据库（已排除数据源自动装配）。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.modelgateway", "com.dam.common"})
public class DamModelGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(DamModelGatewayApplication.class, args);
    }
}
