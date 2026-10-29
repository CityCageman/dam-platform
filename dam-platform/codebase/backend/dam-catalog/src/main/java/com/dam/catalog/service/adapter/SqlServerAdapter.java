package com.dam.catalog.service.adapter;

import lombok.extern.slf4j.Slf4j;
import java.sql.*;
import java.util.*;

/**
 * SQL Server 适配器
 * - catalog = null (JDBC 不使用 catalog)
 * - 使用 INFORMATION_SCHEMA 查询主键
 */
@Slf4j
public class SqlServerAdapter implements DataSourceAdapter {

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "sys", "INFORMATION_SCHEMA", "guest", "db_owner", "db_accessadmin",
            "db_securityadmin", "db_ddladmin", "db_backupoperator",
            "db_datareader", "db_datawriter", "db_denydatareader", "db_denydatawriter"
    );

    @Override
    public boolean supports(Connection conn, String typeKey) {
        return "SQLSERVER".equalsIgnoreCase(typeKey);
    }

    @Override
    public String resolveCatalog(String databaseName) {
        return null;
    }

    @Override
    public List<String> listSchemas(Connection conn, String catalog) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                if (schema != null && !schema.isEmpty() && !isSystemSchema(schema)) {
                    schemas.add(schema);
                }
            }
        }
        return schemas;
    }

    @Override
    public boolean isSystemSchema(String schema) {
        return schema != null && SYSTEM_SCHEMAS.contains(schema);
    }

    @Override
    public Map<String, Set<String>> fetchPrimaryKeys(Connection conn, DatabaseMetaData metaData,
                                                      String catalog, String schema) {
        try {
            return queryPrimaryKeysViaInformationSchema(conn, schema);
        } catch (SQLException e) {
            log.warn("SQL Server INFORMATION_SCHEMA 主键查询失败, 回退到 JDBC: {}", e.getMessage());
        }
        return fallbackGetPrimaryKeys(metaData, catalog, schema);
    }

    private Map<String, Set<String>> queryPrimaryKeysViaInformationSchema(Connection conn, String schema) throws SQLException {
        Map<String, Set<String>> pkMap = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder()
                .append("SELECT kcu.TABLE_NAME, kcu.COLUMN_NAME ")
                .append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc ")
                .append("JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu ")
                .append("  ON tc.CONSTRAINT_CATALOG = kcu.CONSTRAINT_CATALOG ")
                .append("  AND tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA ")
                .append("  AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME ")
                .append("WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'");
        if (schema != null) {
            sql.append(" AND tc.TABLE_SCHEMA = ?");
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (schema != null) {
                ps.setString(1, schema);
            }
            ps.setQueryTimeout(30);
            try (ResultSet pkRs = ps.executeQuery()) {
                while (pkRs.next()) {
                    pkMap.computeIfAbsent(pkRs.getString("TABLE_NAME"), k -> new HashSet<>())
                         .add(pkRs.getString("COLUMN_NAME"));
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
            log.warn("逐个表回退查询主键失败: {}", e.getMessage());
        }
        return pkMap;
    }
}
