package com.dam.lineage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据血缘服务启动类
 * <p>
 * 提供表级血缘关系的查询、SQL 解析构建血缘、影响分析能力；
 * 扫描 com.dam.lineage 与 com.dam.common（公共组件）。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.lineage", "com.dam.common"})
@MapperScan("com.dam.lineage.repository")
public class DamLineageApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(DamLineageApplication.class, args);
    }
}
