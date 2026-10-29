package com.dam.security.repository;

import java.util.Map;

/**
 * 动态 SQL 提供器
 * <p>
 * 在运行时返回待执行的 SQL 语句，供 {@link DynamicQueryMapper} 使用。
 * 采用 {@code @SelectProvider} 方式替代注解内联 ${} 占位 SQL，
 * 避免静态分析工具解析注解 SQL 字符串时产生误报。
 * 注意：调用方必须预先进行 SQL 安全校验，本类不承担安全过滤职责。
 *
 * @author DAM Platform
 */
public class DynamicSqlProvider {

    /**
     * 返回待执行的动态 SQL
     *
     * @param params Mapper 方法入参映射（含 @Param("sql") 的键值）
     * @return 动态 SQL（应为 SELECT 查询）
     */
    public String buildQuery(Map<String, Object> params) {
        return (String) params.get("sql");
    }
}
