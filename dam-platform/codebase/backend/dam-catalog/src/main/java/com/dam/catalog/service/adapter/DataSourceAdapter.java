package com.dam.catalog.service.adapter;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 数据源适配器接口 - 每种数据库类型实现各自的扫描策略
 */
public interface DataSourceAdapter {

    /** 判断当前适配器是否支持该连接 */
    boolean supports(Connection conn, String typeKey);

    /** 获取 catalog 值 (用于 JDBC 的 catalog 参数) */
    String resolveCatalog(String databaseName);

    /** 获取该数据库下需要扫描的 schema 列表 */
    List<String> listSchemas(Connection conn, String catalog) throws SQLException;

    /**
     * 获取指定 schema 下的所有表名（默认实现：JDBC getTables）
     */
    default List<String> listTableNames(Connection conn, String catalog, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                names.add(rs.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    /**
     * 批量获取列信息（默认实现：一次 getColumns 调用）
     */
    default Map<String, List<Map<String, Object>>> fetchColumns(
            DatabaseMetaData metaData, String catalog, String schema,
            List<Map<String, Object>> schemaTables) throws SQLException {
        Map<String, List<Map<String, Object>>> columnsMap = new LinkedHashMap<>();
        for (Map<String, Object> t : schemaTables) {
            columnsMap.put((String) t.get("tableName"), new ArrayList<>());
        }
        try (ResultSet colRs = metaData.getColumns(catalog, schema, "%", "%")) {
            while (colRs.next()) {
                String tableName = colRs.getString("TABLE_NAME");
                List<Map<String, Object>> cols = columnsMap.get(tableName);
                if (cols == null) continue;
                Map<String, Object> col = new HashMap<>();
                col.put("name", colRs.getString("COLUMN_NAME"));
                col.put("type", colRs.getString("TYPE_NAME"));
                col.put("size", safeGetLong(colRs, "COLUMN_SIZE"));
                col.put("nullable", safeGetInt(colRs, "NULLABLE") == 1);
                col.put("defaultValue", colRs.getString("COLUMN_DEF"));
                col.put("comment", colRs.getString("REMARKS"));
                cols.add(col);
            }
        }
        return columnsMap;
    }

    /** 批量获取主键信息 */
    Map<String, Set<String>> fetchPrimaryKeys(Connection conn, DatabaseMetaData metaData,
                                               String catalog, String schema) throws SQLException;

    /**
     * 获取指定 schema 下的外键关系（默认实现：JDBC getImportedKeys）
     * 返回 [{fromTable, fromColumn, toTable, toColumn, fkName}]，
     * 用于自动生成业务表 → 主数据表的外键引用血缘。
     */
    default List<Map<String, Object>> fetchForeignKeys(Connection conn, DatabaseMetaData metaData,
                                                       String catalog, String schema) throws SQLException {
        List<Map<String, Object>> fks = new ArrayList<>();
        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, "%")) {
            while (rs.next()) {
                Map<String, Object> fk = new HashMap<>();
                fk.put("fkName", rs.getString("FK_NAME"));
                fk.put("fromTable", rs.getString("FKTABLE_NAME"));
                fk.put("fromColumn", rs.getString("FKCOLUMN_NAME"));
                fk.put("toTable", rs.getString("PKTABLE_NAME"));
                fk.put("toColumn", rs.getString("PKCOLUMN_NAME"));
                fks.add(fk);
            }
        }
        return fks;
    }

    /** 判断一个 schema 是否为系统 schema（应跳过） */
    default boolean isSystemSchema(String schema) {
        return false;
    }

    /** 安全读取 int */
    default int safeGetInt(ResultSet rs, String columnLabel) {
        try { return rs.getInt(columnLabel); }
        catch (SQLException e) { return 0; }
    }

    /** 安全读取 long */
    default Long safeGetLong(ResultSet rs, String columnLabel) {
        try {
            long val = rs.getLong(columnLabel);
            return rs.wasNull() ? null : val;
        } catch (SQLException e) {
            try {
                Object obj = rs.getObject(columnLabel);
                if (obj instanceof Number) return ((Number) obj).longValue();
            } catch (SQLException ignored) {}
            return null;
        }
    }
}
