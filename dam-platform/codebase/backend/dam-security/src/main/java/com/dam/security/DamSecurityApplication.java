package com.dam.security;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 安全管控服务启动类
 * <p>
 * 提供敏感数据规则、脱敏策略、ABAC 数据权限、数据访问申请、
 * 数据操作审计与自动分类分级能力；跨模块复用 catalog（资产）的 Mapper。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.security", "com.dam.common"})
@MapperScan({"com.dam.security.repository", "com.dam.catalog.repository"})
public class DamSecurityApplication {
    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) { SpringApplication.run(DamSecurityApplication.class, args); }
}
