package com.dam.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * 网关服务启动类
 * <p>
 * 作为请求统一入口，负责 token 存在性校验与路由转发；
 * 网关不直接访问数据库，故排除 DataSourceAutoConfiguration 自动数据源装配。
 *
 * @author DAM Platform
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class DamGatewayApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用（无数据源模式）
        SpringApplication.run(DamGatewayApplication.class, args);
    }
}
