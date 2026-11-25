package com.dam.governance.lifecycle;
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
@SpringBootApplication(scanBasePackages = {"com.dam.governance.lifecycle", "com.dam.platform.common"})
@MapperScan({"com.dam.governance.lifecycle.repository"})
public class GovernanceLifecycleApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) { SpringApplication.run(GovernanceLifecycleApplication.class, args); }
}
