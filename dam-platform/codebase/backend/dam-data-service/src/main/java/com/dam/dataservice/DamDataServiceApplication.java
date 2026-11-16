package com.dam.dataservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据服务（API 管理）启动类
 * <p>
 * 负责数据 API 目录、发布与订阅的管理入口；阶段 1 仅提供骨架接口，
 * 不连接数据库，也不实现真实的 API 发布/订阅流程。
 * 复用 dam-common 的统一响应与异常处理（已排除数据源/MyBatis-Plus 自动装配）。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.dataservice", "com.dam.common"})
public class DamDataServiceApplication {

    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DamDataServiceApplication.class, args);
    }
}
