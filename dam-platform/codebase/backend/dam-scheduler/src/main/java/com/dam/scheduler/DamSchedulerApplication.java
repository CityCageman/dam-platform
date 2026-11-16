package com.dam.scheduler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度服务启动类。
 * <p>
 * 承载调度任务元数据管理、启停、触发记录与 Cron 自动派发；任务与运行记录持久化在
 * dam_scheduler schema。自动派发只写入 PENDING 运行记录，真实 DAG 执行引擎尚未实现。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.scheduler", "com.dam.common"})
@EnableScheduling
@MapperScan("com.dam.scheduler.repository")
public class DamSchedulerApplication {

    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DamSchedulerApplication.class, args);
    }
}
