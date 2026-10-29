package com.dam.quality.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

/**
 * 动态 SQL 查询 Mapper
 * <p>
 * 执行质量规则表达式（自定义 SQL），由 {@link com.dam.quality.service.QualityScanService} 调用。
 * 注意：调用方必须预先进行 SQL 安全校验（白名单仅 SELECT + 黑名单危险关键字），
 * 本 Mapper 不承担安全过滤职责。
 *
 * @author DAM Platform
 */
@Mapper
public interface DynamicQueryMapper {

    /**
     * 执行一条动态 SQL（应为 SELECT 查询）并返回结果集
     *
     * @param sql 动态 SQL（经调用方安全校验后传入）
     * @return 查询结果列表（每行一个 Map）
     */
    @SelectProvider(type = DynamicSqlProvider.class, method = "buildQuery")
    List<Map<String, Object>> executeQuery(@Param("sql") String sql);
}
