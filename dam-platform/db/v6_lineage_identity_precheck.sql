-- =====================================================================
-- v6 血缘节点 schema 化：只读预检（不修改任何数据）
-- 目的：在执行迁移前确认可映射数量、同名表冲突、node_key 重复情况。
-- 前提：db/v4_lineage_layers.sql 已执行（metadata_lineage_source 已有
--       schema_name / datasource_id / table_id 列）。
-- =====================================================================

-- 0. 表是否存在
SELECT to_regclass('dam_metadata.metadata_lineage_source') AS lineage_source,
       to_regclass('dam_metadata.metadata_lineage_hierarchy') AS lineage_hierarchy,
       to_regclass('dam_metadata.metadata_table') AS metadata_table,
       to_regclass('dam_metadata.metadata_schema') AS metadata_schema,
       to_regclass('dam_metadata.metadata_datasource') AS metadata_datasource;

-- 1. 当前列（确认 schema_name/datasource_id/table_id 是否存在）
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'dam_metadata'
  AND table_name = 'metadata_lineage_source'
ORDER BY ordinal_position;

-- 2. 节点规模与已映射情况
SELECT count(*) AS total_nodes,
       count(*) FILTER (WHERE table_id IS NOT NULL) AS mapped_nodes,
       count(*) FILTER (WHERE table_id IS NULL) AS unmapped_nodes,
       count(*) FILTER (WHERE source_code LIKE '%.%') AS qualified_code_nodes
FROM dam_metadata.metadata_lineage_source;

-- 3. 同名表跨 Schema（这些表不能只按裸表名回填，避免张冠李戴）
SELECT lower(table_name) AS table_name,
       count(DISTINCT schema_id) AS schema_count,
       array_agg(DISTINCT schema_id ORDER BY schema_id) AS schema_ids
FROM dam_metadata.metadata_table
WHERE status = 1
GROUP BY lower(table_name)
HAVING count(DISTINCT schema_id) > 1
ORDER BY schema_count DESC, table_name
LIMIT 100;

-- 4. 可映射到唯一表的节点数量（预测回填覆盖率）
WITH unique_names AS (
    SELECT lower(table_name) AS name
    FROM dam_metadata.metadata_table
    WHERE status = 1
    GROUP BY lower(table_name)
    HAVING count(DISTINCT schema_id) = 1
)
SELECT count(*) AS total_nodes,
       count(*) FILTER (WHERE EXISTS (
           SELECT 1
           FROM dam_metadata.metadata_table t
           JOIN dam_metadata.metadata_schema ms ON ms.id = t.schema_id
           WHERE t.status = 1
             AND UPPER(COALESCE(ls.source_type_id, '')) = 'TABLE'
             AND (
                 lower(t.table_name) = lower(ls.source_code)
                 OR lower(ms.schema_name) || '.' || lower(t.table_name) = lower(ls.source_code)
             )
       )) AS matchable_nodes,
       count(*) FILTER (WHERE ls.table_id IS NOT NULL) AS already_mapped
FROM dam_metadata.metadata_lineage_source ls;

-- 5. 预测 node_key 重复（同一逻辑节点是否已有重复编码）
WITH calc AS (
    SELECT id,
           lower(CASE
               WHEN source_code IS NULL OR source_code = '' THEN 'node:' || id
               WHEN schema_name IS NOT NULL AND schema_name <> ''
                    AND lower(left(source_code, length(schema_name) + 1)) <> lower(schema_name) || '.'
                   THEN schema_name || '.' || source_code
               ELSE source_code
           END) AS node_key
    FROM dam_metadata.metadata_lineage_source
)
SELECT node_key, count(*) AS cnt, array_agg(id ORDER BY id) AS ids
FROM calc
GROUP BY node_key
HAVING count(*) > 1
ORDER BY cnt DESC
LIMIT 100;

-- 6. 是否已经存在 node_key（判断脚本是否已跑过）
SELECT count(*) AS node_key_column_exists
FROM information_schema.columns
WHERE table_schema = 'dam_metadata'
  AND table_name = 'metadata_lineage_source'
  AND column_name = 'node_key';
