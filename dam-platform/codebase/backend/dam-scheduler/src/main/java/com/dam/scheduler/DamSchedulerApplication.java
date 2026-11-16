package com.dam.scheduler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 调度服务启动类。
 * <p>
 * 承载调度任务元数据管理、启停与触发记录；任务与运行记录持久化在 dam_scheduler schema，
 * 真实 DAG 执行引擎尚未实现，触发接口只写入 PENDING 运行记录。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.scheduler", "com.dam.common"})
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
