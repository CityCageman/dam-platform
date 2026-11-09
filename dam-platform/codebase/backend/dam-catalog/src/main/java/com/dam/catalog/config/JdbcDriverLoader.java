package com.dam.catalog.config;

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
 * 外部 JDBC 驱动加载器。
 * <p>
 * 人大金仓、达梦等厂商驱动通常不放到 Maven 中央仓库，也不适合直接打进主包。
 * 把驱动 jar 放到 {@code jdbc-drivers/} 目录，启动时自动注册；也可以在运行时按需加载。
 * <p>
 * 目录可通过 {@code dam.jdbc.drivers-dir} 或环境变量 {@code DAM_JDBC_DRIVERS_DIR} 配置。
 */
@Slf4j
@Component
public class JdbcDriverLoader {

    @Value("${dam.jdbc.drivers-dir:jdbc-drivers}")
    private String driversDir;

    private volatile URLClassLoader externalLoader;

    @PostConstruct
    public void loadAtStartup() {
        ensureExternalLoader();
        if (externalLoader == null) return;
        try {
            int count = 0;
            for (Driver driver : ServiceLoader.load(Driver.class, externalLoader)) {
                DriverManager.registerDriver(new DriverShim(driver));
                log.info("已加载外部 JDBC 驱动: {}", driver.getClass().getName());
                count++;
            }
            if (count == 0) log.warn("外部 JDBC 驱动目录存在，但未发现 Driver: {}", driversDir);
        } catch (Exception e) {
            log.warn("加载外部 JDBC 驱动失败: {}", e.getMessage());
        }
    }

    /** 确认驱动类可用；若不在主包中，尝试从外部驱动目录加载并注册 */
    public boolean ensureDriver(String driverClass) {
        if (driverClass == null || driverClass.isBlank()) return false;
        try {
            Class.forName(driverClass);
            return true;
        } catch (ClassNotFoundException ignored) {
            // 继续尝试外部目录
        }
        ensureExternalLoader();
        if (externalLoader == null) return false;
        try {
            Class<?> clazz = Class.forName(driverClass, true, externalLoader);
            Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(driver));
            log.info("按需加载外部 JDBC 驱动: {}", driverClass);
            return true;
        } catch (Exception e) {
            log.warn("未能加载 JDBC 驱动 {}: {}", driverClass, e.getMessage());
            return false;
        }
    }

    private synchronized void ensureExternalLoader() {
        if (externalLoader != null) return;
        File dir = new File(driversDir);
        if (!dir.isAbsolute()) {
            // 兼容 Windows/Linux 下从工作目录启动
            dir = new File(System.getProperty("user.dir"), driversDir);
        }
        if (!dir.isDirectory()) {
            log.info("外部 JDBC 驱动目录不存在: {}", dir.getAbsolutePath());
            return;
        }
        List<URL> urls = new ArrayList<>();
        File[] jars = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                try { urls.add(jar.toURI().toURL()); }
                catch (Exception e) { log.warn("忽略无效驱动 jar {}: {}", jar, e.getMessage()); }
            }
        }
        if (urls.isEmpty()) return;
        externalLoader = new URLClassLoader(urls.toArray(new URL[0]), JdbcDriverLoader.class.getClassLoader());
        log.info("外部 JDBC 驱动目录: {}，发现 {} 个 jar", dir.getAbsolutePath(), urls.size());
    }

    /** DriverManager 注册包装，避免驱动类由外部 ClassLoader 加载后不可见 */
    private record DriverShim(Driver delegate) implements Driver {
        @Override public java.sql.Connection connect(String url, java.util.Properties info) throws SQLException { return delegate.connect(url, info); }
        @Override public boolean acceptsURL(String url) throws SQLException { return delegate.acceptsURL(url); }
        @Override public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException { return delegate.getPropertyInfo(url, info); }
        @Override public int getMajorVersion() { return delegate.getMajorVersion(); }
        @Override public int getMinorVersion() { return delegate.getMinorVersion(); }
        @Override public boolean jdbcCompliant() { return delegate.jdbcCompliant(); }
        @Override public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
    }
}
