package com.dam.lineage.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 血缘中文检索 Mapper
 * <p>
 * 直接检索技术元数据中的中文注释（字段注释 / 表注释），用于“按中文描述找血缘”
 * （例如输入「医嘱号」定位到 dwd_visit_order.order_no，再沿血缘找相关报表与数据表）。
 * 只读查询，不修改任何数据。
 *
 * @author DAM Platform
 */
@Mapper
public interface LineageSearchMapper {

    /**
     * 按关键字检索字段（匹配字段中文注释或字段名）
     *
     * @param keyword 关键字
     * @param limit   最大返回条数
     * @return 字段命中列表（含所属表信息）
     */
    @Select("""
            SELECT c.id              AS column_id,
                   c.column_name     AS column_name,
                   c.column_comment  AS column_comment,
                   c.column_type     AS column_type,
                   t.id              AS table_id,
                   t.table_name      AS table_name,
                   t.table_comment   AS table_comment,
                   t.layer           AS layer
              FROM dam_metadata.metadata_column c
              JOIN dam_metadata.metadata_table t ON t.id = c.table_id
             WHERE (
                     LOWER(COALESCE(c.column_comment, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  OR LOWER(c.column_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   )
               AND c.status = 1
               AND t.status = 1
             ORDER BY CASE WHEN c.column_comment = #{keyword} THEN 0 ELSE 1 END,
                      t.table_name, c.ordinal_position
             LIMIT #{limit}
            """)
    List<Map<String, Object>> searchColumns(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 按关键字检索表（匹配表中文注释或表名）
     *
     * @param keyword 关键字
     * @param limit   最大返回条数
     * @return 表命中列表
     */
    @Select("""
            SELECT t.id            AS table_id,
                   t.table_name    AS table_name,
                   t.table_comment AS table_comment,
                   t.layer         AS layer
              FROM dam_metadata.metadata_table t
             WHERE (
                     LOWER(COALESCE(t.table_comment, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  OR LOWER(t.table_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   )
               AND t.status = 1
             ORDER BY CASE WHEN t.table_comment = #{keyword} THEN 0 ELSE 1 END, t.table_name
             LIMIT #{limit}
            """)
    List<Map<String, Object>> searchTables(@Param("keyword") String keyword, @Param("limit") int limit);
}
