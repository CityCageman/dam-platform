package com.dam.integration.access.service;

import com.dam.common.exception.BusinessException;
import com.dam.integration.access.config.AccessDriverLoader;
import com.dam.integration.access.model.DataSourceTypeDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * 数据接入 JDBC 能力服务
 * <p>
 * 负责内置数据源类型目录、JDBC URL 组装、数据库名解析、驱动推导与连接测试。
 * 密码只通过 JDBC {@link Properties} 交给驱动，不进入日志、响应或 URL。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAccessJdbcService {

    /** 类型编码 -> 类型定义（保持前端展示顺序） */
    private static final Map<String, DataSourceTypeDefinition> TYPES;

    /** 类型列表（不可变） */
    private static final List<DataSourceTypeDefinition> TYPE_LIST;

    /** 类型别名（统一去除空格/下划线/连字符并转大写后匹配） */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    /** JDBC URL 前缀 -> 驱动类（未知类型时兜底推导） */
    private static final Map<String, String> URL_DRIVERS = new LinkedHashMap<>();

    static {
        Map<String, DataSourceTypeDefinition> types = new LinkedHashMap<>();
        register(types, new DataSourceTypeDefinition("POSTGRESQL", "PostgreSQL",
                "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s", 5432));
        register(types, new DataSourceTypeDefinition("MYSQL", "MySQL",
                "com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai", 3306));
        register(types, new DataSourceTypeDefinition("ORACLE", "Oracle",
                "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//%s:%d/%s", 1521));
        register(types, new DataSourceTypeDefinition("SQLSERVER", "SQL Server",
                "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                "jdbc:sqlserver://%s:%d;databaseName=%s", 1433));
        register(types, new DataSourceTypeDefinition("DM8", "达梦",
                "dm.jdbc.driver.DmDriver", "jdbc:dm://%s:%d/%s", 5236));
        register(types, new DataSourceTypeDefinition("KINGBASE", "人大金仓",
                "com.kingbase8.Driver", "jdbc:kingbase8://%s:%d/%s", 54321));
        register(types, new DataSourceTypeDefinition("IRIS", "InterSystems IRIS",
                "com.intersystems.jdbc.IRISDriver", "jdbc:IRIS://%s:%d/%s", 1972));
        TYPES = Collections.unmodifiableMap(types);
        TYPE_LIST = List.copyOf(types.values());

        // 常用别名（含历史 seeder/前端使用的展示名）
        alias("POSTGRES", "POSTGRESQL");
        alias("PG", "POSTGRESQL");
        alias("MARIADB", "MYSQL");
        alias("MSSQL", "SQLSERVER");
        alias("MICROSOFTSQLSERVER", "SQLSERVER");
        alias("DM", "DM8");
        alias("DAMENG", "DM8");
        alias("达梦", "DM8");
        alias("KINGBASE8", "KINGBASE");
        alias("人大金仓", "KINGBASE");
        alias("INTERSYSTEMS", "IRIS");
        alias("INTERSYSTEMSIRIS", "IRIS");
        alias("CACHE", "IRIS");

        URL_DRIVERS.put("jdbc:postgresql:", "org.postgresql.Driver");
        URL_DRIVERS.put("jdbc:mysql:", "com.mysql.cj.jdbc.Driver");
        URL_DRIVERS.put("jdbc:mariadb:", "org.mariadb.jdbc.Driver");
        URL_DRIVERS.put("jdbc:oracle:", "oracle.jdbc.OracleDriver");
        URL_DRIVERS.put("jdbc:sqlserver:", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        URL_DRIVERS.put("jdbc:dm:", "dm.jdbc.driver.DmDriver");
        URL_DRIVERS.put("jdbc:kingbase8:", "com.kingbase8.Driver");
        URL_DRIVERS.put("jdbc:iris:", "com.intersystems.jdbc.IRISDriver");
        URL_DRIVERS.put("jdbc:clickhouse:", "com.clickhouse.jdbc.ClickHouseDriver");
        URL_DRIVERS.put("jdbc:hive2:", "org.apache.hive.jdbc.HiveDriver");
    }

    /** 外部 JDBC 驱动加载器 */
    private final AccessDriverLoader driverLoader;

    /**
     * 注册内置类型并补充编码/展示名别名
     */
    private static void register(Map<String, DataSourceTypeDefinition> types,
                                 DataSourceTypeDefinition definition) {
        types.put(definition.code(), definition);
        ALIASES.put(aliasKey(definition.code()), definition.code());
        ALIASES.put(aliasKey(definition.name()), definition.code());
    }

    /**
     * 登记额外别名
     */
    private static void alias(String alias, String code) {
        ALIASES.put(aliasKey(alias), code);
    }

    /**
     * 别名归一化：去空格/下划线/连字符并转大写
     */
    private static String aliasKey(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-_]+", "");
        return key.isEmpty() ? null : key;
    }

    /**
     * 返回内置数据源类型列表
     *
     * @return 类型列表（保持定义顺序）
     */
    public List<DataSourceTypeDefinition> listTypes() {
        return TYPE_LIST;
    }

    /**
     * 按类型编码/展示名/别名查找内置类型（忽略大小写、空格、下划线、连字符）
     *
     * @param rawType 原始类型
     * @return 类型定义；未知类型返回 null
     */
    public DataSourceTypeDefinition findType(String rawType) {
        String key = aliasKey(rawType);
        if (key == null) {
            return null;
        }
        String code = ALIASES.get(key);
        return code == null ? null : TYPES.get(code);
    }

    /**
     * 返回归一化后的类型编码
     *
     * @param rawType 原始类型
     * @return 内置类型编码；未知类型返回 null
     */
    public String canonicalType(String rawType) {
        DataSourceTypeDefinition definition = findType(rawType);
        return definition == null ? null : definition.code();
    }

    /**
     * 按类型 + 主机/端口/数据库名组装 JDBC URL
     *
     * @param definition   类型定义
     * @param host         主机/IP
     * @param port         端口（可空，使用类型默认端口）
     * @param databaseName 数据库名/namespace
     * @return JDBC URL
     */
    public String buildJdbcUrl(DataSourceTypeDefinition definition, String host,
                               String port, String databaseName) {
        if (definition == null) {
            throw new BusinessException(400, "未知数据源类型，无法自动组装 JDBC URL，请直接提供 jdbcUrl");
        }
        String safeHost = trimToNull(host);
        if (safeHost == null) {
            throw new BusinessException(400, "数据源主机(dsIp)不能为空，或请直接提供 jdbcUrl");
        }
        String safeDatabase = trimToNull(databaseName);
        if (safeDatabase == null) {
            throw new BusinessException(400,
                    "数据库名/命名空间(databaseName)不能为空，或请直接提供 jdbcUrl");
        }
        int actualPort = parsePort(port, definition.defaultPort());
        String template = definition.urlTemplate();

        int hostIdx = template.indexOf("%s");
        if (hostIdx < 0) {
            throw new BusinessException(500, "数据源类型 URL 模板缺少主机占位符: " + definition.code());
        }
        String url = template.substring(0, hostIdx) + safeHost + template.substring(hostIdx + 2);

        int portIdx = url.indexOf("%d");
        if (portIdx >= 0) {
            url = url.substring(0, portIdx) + actualPort + url.substring(portIdx + 2);
        }

        int dbIdx = url.indexOf("%s");
        if (dbIdx >= 0) {
            url = url.substring(0, dbIdx) + safeDatabase + url.substring(dbIdx + 2);
        } else {
            url = url + safeDatabase;
        }
        return url;
    }

    /**
     * 解析端口；为空时使用默认端口，非法值返回 400
     *
     * @param port        端口字符串
     * @param defaultPort 默认端口
     * @return 实际端口
     */
    public int parsePort(String port, int defaultPort) {
        String value = trimToNull(port);
        if (value == null) {
            return defaultPort;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0 || parsed > 65535) {
                throw new BusinessException(400, "端口范围不正确: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "端口格式不正确: " + value);
        }
    }

    /**
     * 从 JDBC URL 解析数据库名/namespace（派生字段，不落库）
     *
     * @param jdbcUrl JDBC URL
     * @return 数据库名；无法解析时返回 null
     */
    public String parseDatabaseName(String jdbcUrl) {
        String url = trimToNull(jdbcUrl);
        if (url == null) {
            return null;
        }
        // SQL Server 使用 ;databaseName=xxx 形式
        if (url.toLowerCase(Locale.ROOT).startsWith("jdbc:sqlserver:")) {
            String lower = url.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf("databasename=");
            if (idx >= 0) {
                String rest = url.substring(idx + "databasename=".length());
                int end = rest.indexOf(';');
                String name = trimToNull(end >= 0 ? rest.substring(0, end) : rest);
                if (name != null) {
                    return name;
                }
            }
        }
        // 去掉查询参数
        int queryIdx = url.indexOf('?');
        if (queryIdx >= 0) {
            url = url.substring(0, queryIdx);
        }
        // 有 authority（//host:port）时只认 authority 之后的路径斜杠，
        // 避免把 jdbc:postgresql://host:5432 里的 // 误判为数据库名
        int authorityIdx = url.indexOf("//");
        if (authorityIdx >= 0) {
            int pathSlashIdx = url.indexOf('/', authorityIdx + 2);
            if (pathSlashIdx >= 0 && pathSlashIdx < url.length() - 1) {
                return trimToNull(url.substring(pathSlashIdx + 1));
            }
            return null;
        }
        // Oracle SID 形式：jdbc:oracle:thin:@host:1521:ORCL
        if (url.toLowerCase(Locale.ROOT).startsWith("jdbc:oracle:")) {
            int colonIdx = url.lastIndexOf(':');
            if (colonIdx >= 0 && colonIdx < url.length() - 1) {
                return trimToNull(url.substring(colonIdx + 1));
            }
        }
        return null;
    }

    /**
     * 未知类型时按 JDBC URL 前缀推导驱动类
     *
     * @param jdbcUrl JDBC URL
     * @return 驱动类；无法推导时返回 null
     */
    public String inferDriver(String jdbcUrl) {
        String url = trimToNull(jdbcUrl);
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : URL_DRIVERS.entrySet()) {
            if (lower.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 建立 JDBC 连接并返回连接元信息；失败时抛出业务异常
     * <p>密码仅通过 {@link Properties} 传给驱动，不拼接到 URL，也不写入日志。
     *
     * @param jdbcUrl     JDBC URL
     * @param driverClass 驱动类
     * @param username    用户名
     * @param password    明文密码
     * @return ok/databaseProduct/databaseVersion/driverName/elapsedMs
     */
    public Map<String, Object> testConnection(String jdbcUrl, String driverClass,
                                              String username, String password) {
        String url = trimToNull(jdbcUrl);
        if (url == null) {
            throw new BusinessException(400, "JDBC URL 不能为空");
        }
        String driver = trimToNull(driverClass);
        if (driver == null) {
            throw new BusinessException(400, "JDBC 驱动类不能为空");
        }
        if (!driverLoader.ensureDriver(driver)) {
            throw new BusinessException(400, "JDBC 驱动未找到: " + driver
                    + "，请把厂商驱动 jar 放到 jdbc-drivers 目录后重启服务");
        }

        Properties props = new Properties();
        props.setProperty("user", username == null ? "" : username);
        props.setProperty("password", password == null ? "" : password);
        props.setProperty("loginTimeout", "10");
        props.setProperty("connectTimeout", "10000");

        long start = System.nanoTime();
        try (Connection connection = DriverManager.getConnection(url, props)) {
            try {
                if (!connection.isValid(5)) {
                    throw new BusinessException(400, "连接失败：连接校验未通过");
                }
            } catch (SQLFeatureNotSupportedException ignored) {
                // 部分国产/老驱动不支持 isValid，能建立连接即视为成功
            }
            DatabaseMetaData meta = connection.getMetaData();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("databaseProduct", metaValue(meta, true));
            result.put("databaseVersion", metaValue(meta, false));
            result.put("driverName", driverName(meta));
            result.put("elapsedMs", (System.nanoTime() - start) / 1_000_000L);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (SQLException e) {
            // 清理异常消息中可能出现的密码，避免通过响应泄露
            String message = sanitize(e.getMessage(), password);
            log.warn("数据源连接测试失败: {}", message);
            throw new BusinessException(400, "连接失败: " + message);
        }
    }

    /**
     * 安全读取数据库产品名/版本
     */
    private static String metaValue(DatabaseMetaData meta, boolean productName) {
        try {
            return productName ? meta.getDatabaseProductName() : meta.getDatabaseProductVersion();
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * 安全读取驱动名称
     */
    private static String driverName(DatabaseMetaData meta) {
        try {
            return meta.getDriverName();
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * 清理异常消息中的密码信息
     *
     * @param message  原始消息
     * @param password 明文密码
     * @return 脱敏消息
     */
    private static String sanitize(String message, String password) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        String sanitized = message;
        if (password != null && !password.isEmpty()) {
            sanitized = sanitized.replace(password, "***");
        }
        // 覆盖 password=xxx / pwd=xxx 等常见驱动回显格式
        sanitized = sanitized.replaceAll("(?i)(password|pwd)\\s*=\\s*[^;\\s&]+", "$1=***");
        return sanitized;
    }

    /**
     * 空安全 trim：空白字符串返回 null
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
