package com.dam.standard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据标准服务启动类
 * <p>
 * 提供标准码表管理、码值维护、标准映射、自动校验与偏差报告能力；
 * 开启定时任务（@EnableScheduling）供 StandardScheduler 周期性自动校验；
 * 跨模块复用 catalog（资产）/operations（工单）的 Mapper。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.standard", "com.dam.common"})
@EnableScheduling
@MapperScan({"com.dam.standard.repository", "com.dam.catalog.repository", "com.dam.operations.repository"})
public class DamStandardApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(DamStandardApplication.class, args);
    }
}
