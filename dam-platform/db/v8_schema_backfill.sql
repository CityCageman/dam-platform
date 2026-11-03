-- =====================================================================
-- v8：修复历史资产/技术元数据的 Schema 归属（可选，先备份）
--
-- 背景：
--   旧版 registerAsset 只把 schemaName 放进 DamAsset 的临时字段，
--   没有写 metadata_table.schema_id；metadata_table/metadata_column
--   也没有 schema_name 列。修复后新注册会自动写 schema_id。
--
-- 本脚本只处理「一个数据源下只有一个 Schema」的情况；
-- 多 Schema 数据源无法从旧数据反推表属于哪个 Schema，请按 Schema
-- 重新扫描/注册一次，新代码会自动补写 schema_id。
--
-- 执行：psql ... -v ON_ERROR_STOP=1 -f v8_schema_backfill.sql
-- =====================================================================
BEGIN;

-- 1) 单 Schema 数据源：补 metadata_table.schema_id
UPDATE dam_metadata.metadata_table t
SET schema_id = s.id
FROM dam_metadata.metadata_schema s
WHERE t.datasource_id = s.datasource_id
  AND (t.schema_id IS NULL OR t.schema_id = 0)
  AND (
      SELECT count(*)
      FROM dam_metadata.metadata_schema s2
      WHERE s2.datasource_id = t.datasource_id
  ) = 1;

-- 2) 字段表跟随所属表补 schema_id
UPDATE dam_metadata.metadata_column c
SET schema_id = t.schema_id
FROM dam_metadata.metadata_table t
WHERE c.table_id = t.id
  AND (c.schema_id IS NULL OR c.schema_id = 0)
  AND t.schema_id IS NOT NULL
  AND t.schema_id <> 0;

COMMIT;

-- 3) 验证：仍存在 schema_id 为空/0 的数据源（多 Schema 场景）
SELECT t.datasource_id,
       count(*) FILTER (WHERE t.schema_id IS NULL OR t.schema_id = 0) AS missing_schema_tables
FROM dam_metadata.metadata_table t
GROUP BY t.datasource_id
HAVING count(*) FILTER (WHERE t.schema_id IS NULL OR t.schema_id = 0) > 0;
