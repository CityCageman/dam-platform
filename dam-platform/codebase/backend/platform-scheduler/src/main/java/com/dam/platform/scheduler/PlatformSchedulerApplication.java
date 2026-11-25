package com.dam.platform.scheduler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度服务启动类。
 * <p>
 * 承载调度任务/DAG 元数据管理、启停、触发记录、Cron 自动派发与基础顺序 SQL DAG
 * 执行；数据持久化在 dam_dpm schema。派发事务提交后异步执行，运行状态由
 * PENDING → RUNNING → SUCCESS/FAILED。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.platform.scheduler", "com.dam.platform.common"})
@EnableScheduling
@MapperScan("com.dam.platform.scheduler.repository")
public class PlatformSchedulerApplication {

    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PlatformSchedulerApplication.class, args);
    }
}
