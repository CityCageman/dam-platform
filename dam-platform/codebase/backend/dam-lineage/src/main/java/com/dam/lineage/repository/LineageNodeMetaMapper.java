package com.dam.lineage.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 血缘节点元信息 Mapper
 * <p>
 * 用于图谱按“数据源层级 / Schema”判断层级：源库表通常没有 ods_/dwd_ 前缀，
 * 必须回到 metadata_table → metadata_schema → metadata_datasource 取 data_layer。
 * 只读查询，查询失败时由调用方降级为表名前缀推断。
 *
 * @author DAM Platform
 */
@Mapper
public interface LineageNodeMetaMapper {

    /**
     * 批量查询血缘节点的数据源层级与 Schema
     *
     * @param ids 血缘节点 ID（metadata_lineage_source.id）
     * @return [{source_id, data_layer, schema_name}]
     */
    @Select("""
            <script>
            SELECT DISTINCT ON (ls.id)
                   ls.id            AS source_id,
                   ds.data_layer    AS data_layer,
                   ms.schema_name   AS schema_name
              FROM dam_metadata.metadata_lineage_source ls
              LEFT JOIN dam_metadata.metadata_table t
                     ON t.table_name = ls.source_code
              LEFT JOIN dam_metadata.metadata_schema ms
                     ON ms.id = t.schema_id
              LEFT JOIN dam_metadata.metadata_datasource ds
                     ON ds.id = t.datasource_id
             WHERE ls.id IN
             <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             ORDER BY ls.id, t.id
            </script>
            """)
    List<Map<String, Object>> selectNodeMeta(@Param("ids") List<Long> ids);

    /**
     * 按表名批量查询数据源层级与 Schema（用于解析落库时写 layer 字段）
     *
     * @param names 表名列表
     * @return [{table_name, data_layer, schema_name}]
     */
    @Select("""
            <script>
            SELECT DISTINCT ON (t.table_name)
                   t.table_name     AS table_name,
                   ds.data_layer    AS data_layer,
                   ms.schema_name   AS schema_name
              FROM dam_metadata.metadata_table t
              LEFT JOIN dam_metadata.metadata_schema ms
                     ON ms.id = t.schema_id
              LEFT JOIN dam_metadata.metadata_datasource ds
                     ON ds.id = t.datasource_id
             WHERE t.table_name IN
             <foreach collection="names" item="name" open="(" separator="," close=")">#{name}</foreach>
             ORDER BY t.table_name, t.id
            </script>
            """)
    List<Map<String, Object>> selectTableLayers(@Param("names") List<String> names);
}
