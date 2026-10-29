package com.dam.operations;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 运营中心服务启动类
 * <p>
 * 提供运营看板、工单中心、资产价值评估、用户行为分析、SLA 管理能力；
 * 跨模块复用 catalog（资产）的 Mapper。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.operations", "com.dam.common"})
@MapperScan({"com.dam.operations.repository", "com.dam.catalog.repository"})
public class DamOperationsApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) { SpringApplication.run(DamOperationsApplication.class, args); }
}
