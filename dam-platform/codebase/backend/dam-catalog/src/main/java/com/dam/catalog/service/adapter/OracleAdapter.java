package com.dam.catalog.service.adapter;

import lombok.extern.slf4j.Slf4j;
import java.sql.*;
import java.util.*;

/**
 * Oracle 适配器
 * - catalog = null (Oracle 不使用 JDBC catalog)
 * - 直接使用用户名作为 schema
 */
@Slf4j
public class OracleAdapter implements DataSourceAdapter {

    @Override
    public boolean supports(Connection conn, String typeKey) {
        return "ORACLE".equalsIgnoreCase(typeKey);
    }

    @Override
    public String resolveCatalog(String databaseName) {
        return null;
    }

    @Override
    public List<String> listSchemas(Connection conn, String catalog) throws SQLException {
        // Oracle 扫描当前用户下的表
        String currentUser;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT USER FROM DUAL")) {
            currentUser = rs.next() ? rs.getString(1) : null;
        }
        if (currentUser != null) {
            return Collections.singletonList(currentUser);
        }
        return Collections.emptyList();
    }

    @Override
    public Map<String, Set<String>> fetchPrimaryKeys(Connection conn, DatabaseMetaData metaData,
                                                      String catalog, String schema) {
        Map<String, Set<String>> pkMap = new LinkedHashMap<>();
        try {
            String sql = "SELECT uc.table_name, ucc.column_name " +
                         "FROM user_constraints uc " +
                         "JOIN user_cons_columns ucc ON uc.constraint_name = ucc.constraint_name " +
                         "WHERE uc.constraint_type = 'P'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(30);
                try (ResultSet pkRs = ps.executeQuery()) {
                    while (pkRs.next()) {
                        pkMap.computeIfAbsent(pkRs.getString("table_name"), k -> new HashSet<>())
                             .add(pkRs.getString("column_name"));
                    }
                }
            }
            return pkMap;
        } catch (SQLException e) {
            log.warn("Oracle 主键查询失败, 回退到 JDBC getPrimaryKeys: {}", e.getMessage());
        }
        return fallbackGetPrimaryKeys(metaData, catalog, schema);
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
