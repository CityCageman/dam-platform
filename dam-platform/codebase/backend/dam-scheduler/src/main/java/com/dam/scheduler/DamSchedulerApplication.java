package com.dam.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 调度服务启动类。
 * <p>
 * 规划承载 DAG 编排、调度计划与依赖管理；当前阶段仅提供状态、任务目录和 DAG 目录查询
 * 以及触发占位接口，不实现数据库持久化与真实调度执行。
 * <p>
 * TODO 后续接入调度引擎与独立 schema 后，再补充 Mapper 扫描和调度执行配置。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.scheduler", "com.dam.common"})
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
