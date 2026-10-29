package com.dam.catalog.service.adapter;

import lombok.extern.slf4j.Slf4j;
import java.sql.*;
import java.util.*;

/**
 * MySQL / Doris / TiDB / OceanBase(MySQL模式) 适配器
 * - catalog = databaseName
 * - 过滤 information_schema, performance_schema, mysql, sys 等系统库
 * - 使用 INFORMATION_SCHEMA 查询主键
 */
@Slf4j
public class MysqlAdapter implements DataSourceAdapter {

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "performance_schema", "mysql", "sys"
    );

    @Override
    public boolean supports(Connection conn, String typeKey) {
        String key = typeKey.toUpperCase();
        return "MYSQL".equals(key) || "DORIS".equals(key);
    }

    @Override
    public String resolveCatalog(String databaseName) {
        return databaseName;
    }

    @Override
    public List<String> listSchemas(Connection conn, String catalog) throws SQLException {
        // MySQL 中 schema = database，只返回用户选择的数据库本身
        if (catalog != null) {
            return Collections.singletonList(catalog);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isSystemSchema(String schema) {
        return schema != null && SYSTEM_SCHEMAS.contains(schema.toLowerCase());
    }

    @Override
    public Map<String, Set<String>> fetchPrimaryKeys(Connection conn, DatabaseMetaData metaData,
                                                      String catalog, String schema) {
        try {
            return queryPrimaryKeysViaInformationSchema(conn, catalog);
        } catch (SQLException e) {
            log.warn("MySQL INFORMATION_SCHEMA 主键查询失败, 回退到 JDBC getPrimaryKeys: {}", e.getMessage());
        }
        return fallbackGetPrimaryKeys(metaData, catalog, schema);
    }

    private Map<String, Set<String>> queryPrimaryKeysViaInformationSchema(Connection conn, String catalog) throws SQLException {
        Map<String, Set<String>> pkMap = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT kcu.TABLE_NAME, kcu.COLUMN_NAME ")
                .append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ")
                .append("JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu ")
                .append("  ON tc.CONSTRAINT_CATALOG = kcu.CONSTRAINT_CATALOG ")
                .append("  AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA ")
                .append("  AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME ")
                .append("WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'");
        if (catalog != null) {
            sql.append(" AND tc.TABLE_SCHEMA = ?");
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (catalog != null) {
                ps.setString(1, catalog);
            }
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pkMap.computeIfAbsent(rs.getString("TABLE_NAME"), k -> new HashSet<>())
                         .add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return pkMap;
    }

    private Map<String, Set<String>> fallbackGetPrimaryKeys(DatabaseMetaData metaData,
                                                             String catalog, String schema) {
        Map<String, Set<String>> pkMap = new LinkedHashMap<>();
        try {
            try (ResultSet tablesRs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (tablesRs.next()) {
                    if (Thread.currentThread().isInterrupted()) break;
                    String tableName = tablesRs.getString("TABLE_NAME");
                    Set<String> pks = new HashSet<>();
                    try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                        while (pkRs.next()) {
                            pks.add(pkRs.getString("COLUMN_NAME"));
                        }
                    }
                    if (!pks.isEmpty()) pkMap.put(tableName, pks);
                }
            }
        } catch (SQLException e) {
            log.warn("逐个表查询主键失败: {}", e.getMessage());
        }
        return pkMap;
    }
}
