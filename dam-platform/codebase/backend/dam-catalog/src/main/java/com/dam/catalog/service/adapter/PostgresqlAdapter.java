package com.dam.catalog.service.adapter;

import lombok.extern.slf4j.Slf4j;
import java.sql.*;
import java.util.*;

/**
 * PostgreSQL 适配器
 *
 * 核心优化：和 IRIS 一样，JDBC 的 getTables()/getColumns() 内部有额外开销。
 * 改用 pg_catalog 直查表列表 + information_schema 查列信息 + pg_catalog 查主键。
 *
 * 扫描策略：
 *  - listTableNames:    SELECT tablename FROM pg_catalog.pg_tables / pg_views
 *  - fetchColumns:      SELECT ... FROM information_schema.columns WHERE table_name = ?
 *  - fetchPrimaryKeys:  pg_catalog.pg_constraint 直查（已有）
 *  - 过滤系统 schema (pg_catalog, information_schema, pg_toast, pg_*)
 */
@Slf4j
public class PostgresqlAdapter implements DataSourceAdapter {

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "pg_catalog", "pg_toast", "pg_temp_1"
    );

    @Override
    public boolean supports(Connection conn, String typeKey) {
        if ("POSTGRESQL".equalsIgnoreCase(typeKey)) return true;
        try {
            String product = conn.getMetaData().getDatabaseProductName();
            return "PostgreSQL".equalsIgnoreCase(product);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String resolveCatalog(String databaseName) {
        return databaseName;
    }

    /** 查询有实际表的 schema，避免 JDBC getSchemas */
    @Override
    public List<String> listSchemas(Connection conn, String catalog) throws SQLException {
        List<String> schemas = new ArrayList<>();
        String sql = "SELECT DISTINCT schemaname FROM pg_catalog.pg_tables " +
                     "WHERE schemaname NOT LIKE 'pg_%' AND schemaname != 'information_schema' " +
                     "ORDER BY schemaname";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                schemas.add(rs.getString(1));
            }
        }
        return schemas;
    }

    @Override
    public boolean isSystemSchema(String schema) {
        if (schema == null) return true;
        if (SYSTEM_SCHEMAS.contains(schema)) return true;
        return schema.startsWith("pg_");
    }

    /** pg_catalog 直查表列表，比 JDBC getTables 快 */
    @Override
    public List<String> listTableNames(Connection conn, String catalog, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        // 表
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = ? ORDER BY tablename")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        }
        // 视图
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT viewname FROM pg_catalog.pg_views WHERE schemaname = ? ORDER BY viewname")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        }
        return names;
    }

    /**
     * 批量获取所有表的列信息（一次SQL查询代替逐表查询，大幅提升性能）
     */
    @Override
    public Map<String, List<Map<String, Object>>> fetchColumns(
            DatabaseMetaData metaData, String catalog, String schema,
            List<Map<String, Object>> schemaTables) throws SQLException {
        Map<String, List<Map<String, Object>>> columnsMap = new LinkedHashMap<>();
        for (Map<String, Object> t : schemaTables) {
            columnsMap.put((String) t.get("tableName"), new ArrayList<>());
        }

        String sql = "SELECT table_name, column_name, data_type, " +
                     "character_maximum_length, numeric_precision, numeric_scale, " +
                     "is_nullable, column_default " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema = ? " +
                     "ORDER BY table_name, ordinal_position";

        Connection conn = metaData.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    List<Map<String, Object>> cols = columnsMap.get(tableName);
                    if (cols == null) continue;
                    Map<String, Object> col = new HashMap<>();
                    col.put("name", rs.getString("column_name"));
                    col.put("type", rs.getString("data_type"));
                    long size = rs.getLong("character_maximum_length");
                    if (rs.wasNull()) size = rs.getLong("numeric_precision");
                    col.put("size", rs.wasNull() ? null : size);
                    col.put("nullable", "YES".equalsIgnoreCase(rs.getString("is_nullable")));
                    col.put("defaultValue", rs.getString("column_default"));
                    col.put("comment", "");
                    cols.add(col);
                }
            }
        }
        log.info("  PostgreSQL 列扫描完成, 共 {} 张表", schemaTables.size());
        return columnsMap;
    }

    /** pg_catalog.pg_constraint 直查主键 */
    @Override
    public Map<String, Set<String>> fetchPrimaryKeys(Connection conn, DatabaseMetaData metaData,
                                                      String catalog, String schema) {
        try {
            return queryPrimaryKeysViaPgCatalog(conn, schema);
        } catch (SQLException e) {
            log.warn("PostgreSQL pg_catalog 主键查询失败, 回退: {}", e.getMessage());
        }
        return fallbackGetPrimaryKeys(metaData, catalog, schema);
    }

    private Map<String, Set<String>> queryPrimaryKeysViaPgCatalog(Connection conn, String schema) throws SQLException {
        Map<String, Set<String>> pkMap = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder()
                .append("SELECT c.relname AS table_name, a.attname AS column_name ")
                .append("FROM pg_catalog.pg_constraint pk ")
                .append("JOIN pg_catalog.pg_class c ON pk.conrelid = c.oid ")
                .append("JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid ")
                .append("JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid ")
                .append("  AND a.attnum = ANY(pk.conkey) ")
                .append("WHERE pk.contype = 'p'");
        if (schema != null) {
            sql.append(" AND n.nspname = ?");
        }
        sql.append(" ORDER BY c.relname, a.attnum");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (schema != null) {
                ps.setString(1, schema);
            }
            ps.setQueryTimeout(30);
            try (ResultSet pkRs = ps.executeQuery()) {
                while (pkRs.next()) {
                    pkMap.computeIfAbsent(pkRs.getString("table_name"), k -> new HashSet<>())
                         .add(pkRs.getString("column_name"));
                }
            }
        }
        return pkMap;
    }

    private Map<String, Set<String>> fallbackGetPrimaryKeys(DatabaseMetaData metaData,
                                                             String catalog, String schema) {
        Map<String, Set<String>> pkMap = new LinkedHashMap<>();
        try (ResultSet tablesRs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tablesRs.next()) {
                if (Thread.currentThread().isInterrupted()) break;
                String tableName = tablesRs.getString("TABLE_NAME");
                Set<String> pks = new HashSet<>();
                try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                    while (pkRs.next()) pks.add(pkRs.getString("COLUMN_NAME"));
                }
                if (!pks.isEmpty()) pkMap.put(tableName, pks);
            }
        } catch (SQLException e) {
            log.warn("逐个表查询主键失败: {}", e.getMessage());
        }
        return pkMap;
    }
}
