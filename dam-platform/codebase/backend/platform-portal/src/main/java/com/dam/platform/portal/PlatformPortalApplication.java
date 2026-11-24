package com.dam.platform.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一门户服务启动类
 * <p>
 * 门户 BFF：只负责能力域、模块目录与首页统计等聚合展示数据，不承载业务数据写入。
 * 复用 dam-common 的统一响应与异常处理；不连接数据库（已排除数据源/MyBatis-Plus 自动装配）。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.platform.portal", "com.dam.common"})
public class PlatformPortalApplication {

    /**
     * 服务入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PlatformPortalApplication.class, args);
    }
}
