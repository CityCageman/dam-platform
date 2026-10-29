package com.dam.catalog.service.adapter;

import lombok.extern.slf4j.Slf4j;
import java.sql.*;
import java.util.*;

/**
 * 国产数据库适配器 - 通用回退方案
 * 对于未被其他适配器识别的数据库类型，使用标准的 JDBC 方式扫描
 * 可通过配置定制 catalog_mode 和 pk_strategy
 */
@Slf4j
public class DomesticAdapter implements DataSourceAdapter {

    /** catalog 模式: database / null */
    private final String catalogMode;
    /** 主键策略: information_schema / jdbc_get_primary_keys */
    private final String pkStrategy;
    /** 要过滤的系统 schema 列表 */
    private final Set<String> systemSchemas;

    public DomesticAdapter() {
        this("database", "information_schema", Set.of("information_schema", "INFORMATION_SCHEMA", "SYS", "SYSTEM", "sys"));
    }

    public DomesticAdapter(String catalogMode, String pkStrategy, Set<String> systemSchemas) {
        this.catalogMode = catalogMode != null ? catalogMode : "database";
        this.pkStrategy = pkStrategy != null ? pkStrategy : "information_schema";
        this.systemSchemas = systemSchemas != null ? systemSchemas : Set.of();
    }

    @Override
    public boolean supports(Connection conn, String typeKey) {
        // 作为兜底适配器，返回 true
        return true;
    }

    @Override
    public String resolveCatalog(String databaseName) {
        return "null".equalsIgnoreCase(catalogMode) ? null : databaseName;
    }

    @Override
    public List<String> listSchemas(Connection conn, String catalog) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                String s = rs.getString("TABLE_SCHEM");
                if (s != null && !s.isEmpty() && !isSystemSchema(s)) schemas.add(s);
            }
        }
        return schemas;
    }

    @Override
    public boolean isSystemSchema(String schema) {
        if (schema == null) return true;
        if (systemSchemas.contains(schema)) return true;
        // PostgreSQL 系国产数据库 (Kingbase, GaussDB) 以 pg_ 开头
        return schema.startsWith("pg_") || schema.startsWith("pg_temp_");
    }

    @Override
    public Map<String, Set<String>> fetchPrimaryKeys(Connection conn, DatabaseMetaData metaData,
                                                      String catalog, String schema) {
        if ("information_schema".equalsIgnoreCase(pkStrategy)) {
            try {
                return queryPrimaryKeysViaInformationSchema(conn, catalog, schema);
            } catch (SQLException e) {
                log.warn("国产数据库 INFORMATION_SCHEMA 主键查询失败, 回退到 JDBC getPrimaryKeys: {}", e.getMessage());
            }
        }
        return fallbackGetPrimaryKeys(metaData, catalog, schema);
    }

    private Map<String, Set<String>> queryPrimaryKeysViaInformationSchema(Connection conn, String catalog, String schema) throws SQLException {
        Map<String, Set<String>> pkMap = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT kcu.table_name, kcu.column_name ")
                .append("FROM information_schema.table_constraints tc ")
                .append("JOIN information_schema.key_column_usage kcu ")
                .append("  ON tc.constraint_catalog = kcu.constraint_catalog ")
                .append("  AND tc.constraint_schema = kcu.constraint_schema ")
                .append("  AND tc.constraint_name = kcu.constraint_name ")
                .append("WHERE tc.constraint_type = 'PRIMARY KEY'");
        if (catalog != null) {
            sql.append(" AND tc.table_catalog = ?");
        }
        if (schema != null) {
            sql.append(" AND tc.table_schema = ?");
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (catalog != null) {
                ps.setString(idx++, catalog);
            }
            if (schema != null) {
                ps.setString(idx, schema);
            }
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pkMap.computeIfAbsent(rs.getString("table_name"), k -> new HashSet<>())
                         .add(rs.getString("column_name"));
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
