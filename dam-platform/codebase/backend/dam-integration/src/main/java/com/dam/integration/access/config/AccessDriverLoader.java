package com.dam.integration.access.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 数据接入外部 JDBC 驱动加载器
 * <p>
 * PostgreSQL 驱动由服务自身依赖提供；MySQL/Oracle/SQL Server/达梦/人大金仓/IRIS 等
 * 厂商驱动通常不进入主包。把驱动 jar 放到 {@code jdbc-drivers/} 目录（或
 * {@code dam.jdbc.drivers-dir} 指定目录）即可在不改依赖的情况下测试连接。
 *
 * @author DAM Platform
 */
@Slf4j
@Component
public class AccessDriverLoader {

    /** 外部驱动目录，支持相对工作目录 */
    @Value("${dam.jdbc.drivers-dir:jdbc-drivers}")
    private String driversDir;

    /** 外部驱动类加载器（懒加载，避免目录不存在时反复扫描） */
    private volatile URLClassLoader externalLoader;

    /**
     * 启动时注册外部驱动目录中的 Driver
     */
    @PostConstruct
    public void loadAtStartup() {
        URLClassLoader loader = ensureExternalLoader();
        if (loader == null) {
            return;
        }
        try {
            int count = 0;
            for (Driver driver : ServiceLoader.load(Driver.class, loader)) {
                DriverManager.registerDriver(new DriverShim(driver));
                log.info("已加载外部 JDBC 驱动: {}", driver.getClass().getName());
                count++;
            }
            if (count == 0) {
                log.warn("外部 JDBC 驱动目录存在，但未发现 Driver: {}", driversDir);
            }
        } catch (Exception e) {
            log.warn("加载外部 JDBC 驱动失败: {}", e.getMessage());
        }
    }

    /**
     * 确认驱动类可用；不在主包中时尝试从外部驱动目录按需加载并注册
     *
     * @param driverClass 驱动类全限定名
     * @return true 表示已可用
     */
    public boolean ensureDriver(String driverClass) {
        if (driverClass == null || driverClass.isBlank()) {
            return false;
        }
        try {
            Class.forName(driverClass);
            return true;
        } catch (ClassNotFoundException ignored) {
            // 继续尝试外部驱动目录
        }
        URLClassLoader loader = ensureExternalLoader();
        if (loader == null) {
            return false;
        }
        synchronized (this) {
            try {
                Class<?> clazz = Class.forName(driverClass, true, loader);
                Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
                DriverManager.registerDriver(new DriverShim(driver));
                log.info("按需加载外部 JDBC 驱动: {}", driverClass);
                return true;
            } catch (Exception e) {
                log.warn("未能加载 JDBC 驱动 {}: {}", driverClass, e.getMessage());
                return false;
            }
        }
    }

    /**
     * 懒加载外部驱动目录的 ClassLoader
     *
     * @return ClassLoader；目录不存在或无 jar 时返回 null
     */
    private synchronized URLClassLoader ensureExternalLoader() {
        if (externalLoader != null) {
            return externalLoader;
        }
        File dir = new File(driversDir);
        if (!dir.isAbsolute()) {
            // 兼容 Windows/Linux 下从工作目录启动
            dir = new File(System.getProperty("user.dir"), driversDir);
        }
        if (!dir.isDirectory()) {
            log.info("外部 JDBC 驱动目录不存在: {}", dir.getAbsolutePath());
            return null;
        }
        List<URL> urls = new ArrayList<>();
        File[] jars = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                try {
                    urls.add(jar.toURI().toURL());
                } catch (Exception e) {
                    log.warn("忽略无效驱动 jar {}: {}", jar, e.getMessage());
                }
            }
        }
        if (urls.isEmpty()) {
            return null;
        }
        externalLoader = new URLClassLoader(urls.toArray(new URL[0]),
                AccessDriverLoader.class.getClassLoader());
        log.info("外部 JDBC 驱动目录: {}，发现 {} 个 jar", dir.getAbsolutePath(), urls.size());
        return externalLoader;
    }

    /**
     * DriverManager 注册包装，避免外部 ClassLoader 加载的驱动对 DriverManager 不可见
     */
    private record DriverShim(Driver delegate) implements Driver {

        @Override
        public java.sql.Connection connect(String url, java.util.Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info)
                throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }
}
