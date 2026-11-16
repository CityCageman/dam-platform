package com.dam.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据集成服务启动类
 * <p>
 * 负责数据源连接器目录与集成任务编排。阶段 1 仅提供静态连接器目录和任务查询骨架，
 * 不连接数据库、不执行真实同步逻辑；复用 dam-common 的统一响应与异常处理。
 * <p>
 * 数据源与 MyBatis-Plus 自动装配已在 application.yml 中排除，待后续接入
 * dam_integration 库表时再开启。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.integration", "com.dam.common"})
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
