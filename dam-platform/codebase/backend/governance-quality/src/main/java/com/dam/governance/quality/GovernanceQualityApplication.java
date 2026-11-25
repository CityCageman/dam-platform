package com.dam.governance.quality;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据质量服务启动类
 * <p>
 * 提供质量规则管理、检测执行、趋势报表能力；
 * 开启定时任务（@EnableScheduling）供 QualityScheduler 周期性执行检测；
 * 复用 operations 模块的 Mapper。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.governance.quality", "com.dam.platform.common"})
@EnableScheduling
@MapperScan({"com.dam.governance.quality.repository"})
public class GovernanceQualityApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) { SpringApplication.run(GovernanceQualityApplication.class, args); }
}
