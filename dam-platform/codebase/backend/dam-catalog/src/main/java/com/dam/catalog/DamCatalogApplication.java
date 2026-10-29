package com.dam.catalog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据目录（Catalog）服务启动类
 * <p>
 * 核心服务，提供资产目录、元数据管理、模型管理、数据源扫描、字段同步、批量导入等能力；
 * 扫描 com.dam.catalog 与 com.dam.common（公共组件）。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.catalog", "com.dam.common"})
@MapperScan("com.dam.catalog.repository")
public class DamCatalogApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(DamCatalogApplication.class, args);
    }
}
