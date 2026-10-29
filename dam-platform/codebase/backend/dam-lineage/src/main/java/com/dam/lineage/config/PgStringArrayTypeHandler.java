package com.dam.lineage.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL TEXT 数组类型处理器（List&lt;String&gt; ↔ text[]）
 * 用于 metadata_column_calc_dependency.depends_on_column_names 列
 */
@MappedTypes({List.class})
public class PgStringArrayTypeHandler extends BaseTypeHandler<List<String>> {

    /** 数组元素类型名 */
    private static final String ARRAY_TYPE = "text";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        Array arr = ps.getConnection().createArrayOf(ARRAY_TYPE, parameter.toArray());
        ps.setArray(i, arr);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toList(rs.getArray(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toList(rs.getArray(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toList(cs.getArray(columnIndex));
    }

    private List<String> toList(Array arr) throws SQLException {
        if (arr == null) return null;
        Object[] objs = (Object[]) arr.getArray();
        List<String> list = new ArrayList<>(objs.length);
        for (Object o : objs) {
            list.add(o == null ? null : String.valueOf(o));
        }
        return list;
    }
}
