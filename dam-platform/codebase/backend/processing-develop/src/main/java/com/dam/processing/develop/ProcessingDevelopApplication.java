package com.dam.processing.develop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据开发服务启动类
 * <p>
 * 负责 SQL/Python/Shell 任务定义的持久化、版本记录与发布状态管理。任务与版本
 * 存放在 PostgreSQL 的 {@code dam_dev} schema；本服务只管理任务定义与生命周期，
 * 不实现真实的 SQL/Python 执行引擎。复用 dam-common 的统一响应、异常处理与分页。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.processing.develop", "com.dam.common"})
@MapperScan("com.dam.processing.develop.repository")
public class ProcessingDevelopApplication {

    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ProcessingDevelopApplication.class, args);
    }
}
