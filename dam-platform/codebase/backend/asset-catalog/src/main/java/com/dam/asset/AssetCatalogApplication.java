package com.dam.asset;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据目录（Catalog）服务启动类
 * <p>
 * 核心服务，提供资产目录、元数据管理、模型管理、数据源扫描、字段同步、批量导入等能力；
 * 扫描 com.dam.asset 与 com.dam.platform.common（公共组件）。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.asset", "com.dam.analytics", "com.dam.platform", "com.dam.platform.common"})
@MapperScan({"com.dam.asset.repository", "com.dam.analytics.report.repository", "com.dam.platform.system.repository", "com.dam.asset.catalog.repository", "com.dam.asset.metadata.repository"})
@EnableScheduling
public class AssetCatalogApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(AssetCatalogApplication.class, args);
    }
}
