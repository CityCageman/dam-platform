package com.dam.quality.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 质量规则目标表查询
 * <p>
 * 规则表达式可能只写条件（如 patient_id IS NULL），执行时需要用目标表补全 FROM，
 * 并单独统计目标表总记录数。
 */
@Mapper
public interface QualityTableMapper {

    /**
     * 查询技术元数据表名
     *
     * @param tableId metadata_table.id
     * @return 表名（不存在返回 null）
     */
    @Select("SELECT table_name FROM dam_metadata.metadata_table WHERE id = #{tableId} AND status = 1 LIMIT 1")
    String findTableName(@Param("tableId") Long tableId);
}
