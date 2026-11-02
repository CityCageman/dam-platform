-- =====================================================================
-- v6 迁移校验（迁移前后各执行一次）
-- 重点：source_code/id 校验和必须完全一致；node_key 应全部有值。
-- =====================================================================

-- 1. 旧身份指纹：迁移前后对比必须一致（证明没改旧数据）
SELECT count(*) AS total_nodes,
       md5(string_agg(id::text || ':' || COALESCE(source_code, ''), ',' ORDER BY id)) AS identity_checksum
FROM dam_metadata.metadata_lineage_source;

-- 2. 新列覆盖率
SELECT count(*) AS total_nodes,
       count(*) FILTER (WHERE node_key IS NOT NULL AND node_key <> '') AS keyed_nodes,
       count(*) FILTER (WHERE table_id IS NOT NULL) AS mapped_nodes,
       count(*) FILTER (WHERE schema_name IS NOT NULL AND schema_name <> '') AS schema_filled
FROM dam_metadata.metadata_lineage_source;

-- 3. node_key 重复（允许存在，但需人工确认后再建唯一索引）
SELECT node_key, count(*) AS cnt, array_agg(id ORDER BY id) AS ids
FROM dam_metadata.metadata_lineage_source
GROUP BY node_key
HAVING count(*) > 1
ORDER BY cnt DESC
LIMIT 50;

-- 4. 索引有效性（CONCURRENTLY 失败会留下 invalid 索引）
SELECT i.relname AS index_name,
       ix.indisvalid,
       ix.indisready,
       pg_get_indexdef(ix.indexrelid) AS definition
FROM pg_class i
JOIN pg_index ix ON ix.indexrelid = i.oid
JOIN pg_class t ON t.oid = ix.indrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
WHERE n.nspname = 'dam_metadata'
  AND t.relname = 'metadata_lineage_source'
  AND i.relname IN ('idx_lineage_source_node_key', 'uq_lineage_source_node_key');
