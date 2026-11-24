package com.dam.access.integration.access.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置数据源类型定义
 * <p>
 * 供数据接入服务组装 JDBC URL、推导驱动类，并以前端可消费的
 * {@code code/name/driver/urlTemplate/defaultPort} 结构返回。
 *
 * @param code        类型编码，如 POSTGRESQL
 * @param name        展示名称，如 PostgreSQL
 * @param driver      JDBC 驱动类全限定名
 * @param urlTemplate JDBC URL 模板，占位符依次为 host、port、databaseName
 * @param defaultPort 默认端口
 * @author DAM Platform
 */
public record DataSourceTypeDefinition(String code,
                                       String name,
                                       String driver,
                                       String urlTemplate,
                                       int defaultPort) {

    /**
     * 转换为前端接口返回结构
     *
     * @return code/name/driver/urlTemplate/defaultPort
     */
    public Map<String, Object> toMap() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", name);
        // 兼容旧前端（catalog 类型接口字段名）
        item.put("configCode", code);
        item.put("configName", name);
        item.put("driver", driver);
        item.put("urlTemplate", urlTemplate);
        item.put("defaultPort", defaultPort);
        return item;
    }
}
