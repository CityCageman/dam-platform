package com.dam.integration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据集成服务启动类
 * <p>
 * 负责数据源连接器目录与集成任务管理。任务配置持久化到 dam_integration.integration_job，
 * 复用 dam-common 的统一响应、异常处理与 MyBatis-Plus 配置；当前不包含真实数据同步执行引擎。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.integration", "com.dam.common"})
@MapperScan("com.dam.integration.repository")
public class DamIntegrationApplication {

    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DamIntegrationApplication.class, args);
    }
}
