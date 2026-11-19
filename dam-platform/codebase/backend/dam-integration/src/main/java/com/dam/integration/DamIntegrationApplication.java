package com.dam.integration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据集成服务启动类
 * <p>
 * 负责数据源连接器目录、集成任务管理与真实 JDBC 全量同步执行。任务配置持久化到
 * dam_integration.integration_job，运行记录写入 integration_run；同步执行由有界线程池
 * 异步完成，复用 dam-common 的统一响应、异常处理与 MyBatis-Plus 配置。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.integration", "com.dam.common"})
@MapperScan({"com.dam.integration.repository", "com.dam.integration.access.repository"})
@EnableScheduling
public class DamIntegrationApplication {

    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DamIntegrationApplication.class, args);
    }
}
