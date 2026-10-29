package com.dam.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证服务启动类
 * <p>
 * 提供登录、登出、当前用户查询能力：
 * 扫描 com.dam.auth 与 com.dam.common（公共组件：统一返回/异常处理/MyBatis-Plus配置）
 * 两个包，并自动装配 com.dam.auth.repository 下的 Mapper。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.auth", "com.dam.common"})
@MapperScan("com.dam.auth.repository")
public class DamAuthApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(DamAuthApplication.class, args);
    }
}
