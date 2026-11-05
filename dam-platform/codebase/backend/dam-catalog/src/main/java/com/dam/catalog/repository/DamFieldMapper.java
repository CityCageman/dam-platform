package com.dam.catalog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.entity.DamField;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DamFieldMapper extends BaseMapper<DamField> {

    /** 批量写入字段，避免大表逐字段 INSERT 往返 */
    @Insert("""
            <script>
            INSERT INTO dam_metadata.metadata_column
              (datasource_id, schema_id, table_id, column_name, column_type, column_length,
               is_nullable, is_primary_key, default_value, column_comment, reference_table_name,
               ordinal_position, created_at, created_by, updated_at, updated_by)
            VALUES
            <foreach collection="list" item="f" separator=",">
              (#{f.datasourceId}, #{f.schemaId}, #{f.tableId}, #{f.fieldName}, #{f.dataType}, #{f.dataLength},
               #{f.isNullable}, #{f.isPrimaryKey}, #{f.defaultValue}, #{f.comment}, #{f.referenceTableName},
               #{f.sortOrder}, #{f.createdAt}, #{f.createdBy}, #{f.updatedAt}, #{f.updatedBy})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<DamField> list);
}
