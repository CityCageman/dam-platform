package com.dam.lifecycle;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 生命周期管理服务启动类
 * <p>
 * 提供数据生命周期策略管理、存储分层、数据销毁审批能力；
 * 跨模块复用 catalog（资产）/operations（使用统计）的 Mapper。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.lifecycle", "com.dam.common"})
@MapperScan({"com.dam.lifecycle.repository", "com.dam.catalog.repository", "com.dam.operations.repository"})
public class DamLifecycleApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) { SpringApplication.run(DamLifecycleApplication.class, args); }
}
