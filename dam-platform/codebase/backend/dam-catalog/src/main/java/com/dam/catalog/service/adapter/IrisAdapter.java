package com.dam.catalog.service.adapter;

import lombok.extern.slf4j.Slf4j;
import java.sql.*;
import java.util.*;

/**
 * InterSystems IRIS 适配器
 * 扫描策略：
 *  扫描阶段：仅获取表名（listTableNames），跳过列和主键（效率原因，IRIS 表太多时逐表查列极慢）
 *  按需同步：用户选中表后，通过 syncTableFields 接口单表获取字段
 *  - listSchemas: SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA
 *  - listTableNames: SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
 */
@Slf4j
public class IrisAdapter implements DataSourceAdapter {

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "%SYS", "HSCUSTOM", "HSACCESS",
            "Ens_Config", "Ens_Util", "Ens_Rule", "Ens_Event", "HS_Gateway",
            "HSLIB", "EnsLib", "EnsPortal", "SYS", "SYSTEM", "INFORMATION_SCHEMA"
    );

    @Override
    public boolean supports(Connection conn, String typeKey) {
        return "IRIS".equalsIgnoreCase(typeKey);
    }

    @Override
    public String resolveCatalog(String databaseName) {
        return databaseName;
    }

    /** 使用 SCHEMATA 视图查询所有 schema */
    @Override
    public List<String> listSchemas(Connection conn, String catalog) throws SQLException {
        List<String> schemas = new ArrayList<>();
        String sql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA ORDER BY SCHEMA_NAME";
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(30);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String schema = rs.getString(1);
                    if (!isSystemSchema(schema)) {
                        schemas.add(schema);
                    }
                }
            }
        }
        return schemas;
    }

    @Override
    public boolean isSystemSchema(String schema) {
        if (schema == null) return true;
        if (SYSTEM_SCHEMAS.contains(schema)) return true;
        return schema.startsWith("%") || schema.startsWith("Ens_") || schema.startsWith("HS_");
    }

    /** 直接 SQL 查 INFORMATION_SCHEMA.TABLES */
    @Override
    public List<String> listTableNames(Connection conn, String catalog, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                     "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE IN ('BASE TABLE', 'VIEW') " +
                     "ORDER BY TABLE_NAME";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }

    /** 扫描阶段不获取列（由按需同步接口替代），返回空 */
    @Override
    public Map<String, List<Map<String, Object>>> fetchColumns(
            DatabaseMetaData metaData, String catalog, String schema,
            List<Map<String, Object>> schemaTables) {
        return Collections.emptyMap();
    }

    /** 扫描阶段不获取主键（由按需同步接口替代），返回空 */
    @Override
    public Map<String, Set<String>> fetchPrimaryKeys(Connection conn, DatabaseMetaData metaData,
                                                      String catalog, String schema) {
        return Collections.emptyMap();
    }

    // ============================================================
    //  以下方法供 syncTableFields 直接调用（单表字段同步）
    // ============================================================

    /** 获取单表的列信息 */
    public List<Map<String, Object>> fetchColumnsForTable(Connection conn, String schema, String tableName) {
        List<Map<String, Object>> cols = new ArrayList<>();
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, " +
                     "CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, " +
                     "IS_NULLABLE, COLUMN_DEFAULT " +
                     "FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                     "ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> col = new HashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("DATA_TYPE"));
                    long size = rs.getLong("CHARACTER_MAXIMUM_LENGTH");
                    if (rs.wasNull()) size = rs.getLong("NUMERIC_PRECISION");
                    col.put("size", rs.wasNull() ? null : size);
                    col.put("nullable", "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                    col.put("defaultValue", rs.getString("COLUMN_DEFAULT"));
                    col.put("comment", "");
                    cols.add(col);
                }
            }
        } catch (SQLException e) {
            log.warn("获取表 [{}] 字段信息失败: {}", tableName, e.getMessage());
        }
        return cols;
    }

    /** 获取单表的主键列名集合 */
    public Set<String> fetchPrimaryKeysForTable(Connection conn, String schema, String tableName) {
        Set<String> pks = new HashSet<>();
        String sql = "SELECT kcu.COLUMN_NAME " +
                     "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                     "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu " +
                     "  ON tc.CONSTRAINT_CATALOG = kcu.CONSTRAINT_CATALOG " +
                     "  AND tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA " +
                     "  AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME " +
                     "WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'" +
                     "  AND tc.TABLE_SCHEMA = ? AND kcu.TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pks.add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (SQLException e) {
            log.warn("获取表 [{}] 主键信息失败: {}", tableName, e.getMessage());
        }
        return pks;
    }

    /**
     * 获取单表的索引信息（IRIS 提供 INFORMATION_SCHEMA.INDEXES，JDBC getIndexInfo 对 IRIS 驱动支持不全）
     * 返回 [{indexName, unique, isPrimaryKey, type, columns[]}]
     */
    public List<Map<String, Object>> fetchIndexesForTable(Connection conn, String schema, String tableName) {
        List<Map<String, Object>> indexes = new ArrayList<>();
        String sql = "SELECT Index_Name, Column_Name, Ordinal_Position, Primary_Key, Non_Unique " +
                     "FROM INFORMATION_SCHEMA.INDEXES " +
                     "WHERE Table_Schema = ? AND Table_Name = ? " +
                     "ORDER BY Index_Name, Ordinal_Position";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            Map<String, Map<String, Object>> indexMap = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String indexName = rs.getString("Index_Name");
                    if (indexName == null) continue;
                    Map<String, Object> idx = indexMap.get(indexName);
                    if (idx == null) {
                        idx = new LinkedHashMap<>();
                        idx.put("indexName", indexName);
                        try {
                            idx.put("unique", rs.getInt("Non_Unique") == 0);
                            idx.put("isPrimaryKey", rs.getInt("Primary_Key") == 1);
                        } catch (SQLException ex) {
                            idx.put("unique", false);
                            idx.put("isPrimaryKey", false);
                        }
                        idx.put("type", "");
                        idx.put("columns", new ArrayList<String>());
                        indexMap.put(indexName, idx);
                    }
                    @SuppressWarnings("unchecked")
                    List<String> cols = (List<String>) idx.get("columns");
                    cols.add(rs.getString("Column_Name"));
                }
            }
            indexes.addAll(indexMap.values());
        } catch (SQLException e) {
            log.warn("获取表 [{}] 索引信息失败: {}", tableName, e.getMessage());
        }
        return indexes;
    }
}
