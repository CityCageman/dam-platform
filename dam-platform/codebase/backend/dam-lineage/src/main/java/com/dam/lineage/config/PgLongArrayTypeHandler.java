package com.dam.lineage.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL BIGINT 数组类型处理器（List&lt;Long&gt; ↔ bigint[]）
 * 用于 metadata_column_calc_dependency.depends_on_column_ids 列
 */
@MappedTypes({List.class})
public class PgLongArrayTypeHandler extends BaseTypeHandler<List<Long>> {

    /** 数组元素类型名 */
    private static final String ARRAY_TYPE = "bigint";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Long> parameter, JdbcType jdbcType) throws SQLException {
        Array arr = ps.getConnection().createArrayOf(ARRAY_TYPE, parameter.toArray());
        ps.setArray(i, arr);
    }

    @Override
    public List<Long> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toList(rs.getArray(columnName));
    }

    @Override
    public List<Long> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toList(rs.getArray(columnIndex));
    }

    @Override
    public List<Long> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toList(cs.getArray(columnIndex));
    }

    private List<Long> toList(Array arr) throws SQLException {
        if (arr == null) return null;
        Object[] objs = (Object[]) arr.getArray();
        List<Long> list = new ArrayList<>(objs.length);
        for (Object o : objs) {
            list.add(o == null ? null : ((Number) o).longValue());
        }
        return list;
    }
}
