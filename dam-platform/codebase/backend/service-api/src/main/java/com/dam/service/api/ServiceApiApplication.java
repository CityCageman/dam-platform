package com.dam.service.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据服务（数据 API 管理）启动类
 * <p>
 * 负责数据 API 目录、发布/下线与订阅关系的管理入口。元数据持久化到
 * PostgreSQL 的 {@code dam_data_service} schema；本服务提供基础的 OpenAPI
 * 运行入口（API Key/平台登录鉴权、基础限流与调用审计），不实现完整 API 网关
 * （路由、转发等）。
 * 复用 dam-common 的统一响应、异常处理、分页与 JSONB 类型处理器。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.service.api", "com.dam.common"})
@MapperScan("com.dam.service.api.repository")
public class ServiceApiApplication {

    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ServiceApiApplication.class, args);
    }
}
