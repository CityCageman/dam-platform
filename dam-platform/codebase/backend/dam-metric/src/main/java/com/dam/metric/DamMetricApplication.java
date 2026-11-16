package com.dam.metric;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 指标中心（Metric）服务启动类。
 * <p>
 * 从 dam-catalog 中拆出的独立领域服务，独占 PostgreSQL 的 {@code dam_metric} schema：
 * {@code metric_definition}、{@code metric_value} 两张表只允许本服务读写，catalog 通过
 * {@link com.dam.metric.controller.MetricController} 暴露的内部只读接口获取指标数据。
 * <p>
 * 指标 SQL 生成不再依赖 catalog 的 ReportService，而是通过
 * {@link com.dam.metric.client.CatalogMetricAiClient} 调用 catalog 内部 AI 接口完成，
 * 避免本服务直接访问 catalog 的报表/数据源表。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.metric", "com.dam.common"})
@MapperScan("com.dam.metric.repository")
public class DamMetricApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DamMetricApplication.class, args);
    }
}
