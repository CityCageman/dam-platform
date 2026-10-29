package com.dam.lineage.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;

/**
 * PostgreSQL JSONB 类型处理器
 * 使用反射创建 PGobject，避免编译期对 postgresql 驱动的直接依赖
 */
@MappedTypes({String.class})
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    /** PostgreSQL PGobject 类全限定名（反射加载，避免编译期依赖驱动） */
    private static final String PG_OBJECT_CLASS = "org.postgresql.util.PGobject";

    /**
     * 写入非空参数：将字符串包装为 PGobject（type=jsonb）后写入
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        try {
            // 反射创建 PGobject 并设置 jsonb 类型与值
            Class<?> pgObjectClass = Class.forName(PG_OBJECT_CLASS);
            Object pgObject = pgObjectClass.getDeclaredConstructor().newInstance();
            pgObjectClass.getMethod("setType", String.class).invoke(pgObject, "jsonb");
            pgObjectClass.getMethod("setValue", String.class).invoke(pgObject, parameter);
            ps.setObject(i, pgObject);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            // 回退：直接插入字符串
            ps.setObject(i, parameter, java.sql.Types.OTHER);
        }
    }

    /**
     * 按列名读取结果（PGobject 直接取字符串）
     */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object obj = rs.getObject(columnName);
        return obj == null ? null : obj.toString();
    }

    /**
     * 按列索引读取结果
     */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object obj = rs.getObject(columnIndex);
        return obj == null ? null : obj.toString();
    }

    /**
     * 存储过程调用结果读取
     */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object obj = cs.getObject(columnIndex);
        return obj == null ? null : obj.toString();
    }
}
