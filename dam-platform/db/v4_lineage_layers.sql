-- =====================================================================
-- v4：血缘层级字段（P0）
-- 目标：为“跨层血缘”补齐持久化层级字段，并回填历史数据。
-- 说明：
--   1. 本脚本只加列/回填，不删除、不改动已有业务数据；
--   2. 使用 IF NOT EXISTS / to_regclass 兼容重复执行与缺表环境；
--   3. 代码侧图谱已支持按表名前缀实时推断层级，本脚本用于持久化与检索优化。
-- =====================================================================

DO $$
BEGIN
    -- 技术元数据表：数仓分层（SOURCE/ODS/DWD/DIM/DWS/ADS）
    IF to_regclass('dam_metadata.metadata_table') IS NOT NULL THEN
        ALTER TABLE dam_metadata.metadata_table ADD COLUMN IF NOT EXISTS layer varchar(16);
    END IF;

    -- 血缘节点：层级、来源数据源、schema、表 id
    IF to_regclass('dam_metadata.metadata_lineage_source') IS NOT NULL THEN
        ALTER TABLE dam_metadata.metadata_lineage_source ADD COLUMN IF NOT EXISTS layer varchar(16);
        ALTER TABLE dam_metadata.metadata_lineage_source ADD COLUMN IF NOT EXISTS datasource_id bigint;
        ALTER TABLE dam_metadata.metadata_lineage_source ADD COLUMN IF NOT EXISTS schema_name varchar(100);
        ALTER TABLE dam_metadata.metadata_lineage_source ADD COLUMN IF NOT EXISTS table_id bigint;
    END IF;

    -- 分层血缘边：上下游层级 + 是否跨层
    IF to_regclass('dam_metadata.metadata_lineage_hierarchy') IS NOT NULL THEN
        ALTER TABLE dam_metadata.metadata_lineage_hierarchy ADD COLUMN IF NOT EXISTS upstream_layer varchar(16);
        ALTER TABLE dam_metadata.metadata_lineage_hierarchy ADD COLUMN IF NOT EXISTS downstream_layer varchar(16);
        ALTER TABLE dam_metadata.metadata_lineage_hierarchy ADD COLUMN IF NOT EXISTS is_cross_layer boolean DEFAULT false;
    END IF;
END $$;

-- 1. 表层级：优先取数据源层级（源库表名无 ods_/dwd_ 前缀），其次按 schema 名称推断
UPDATE dam_metadata.metadata_table t
SET layer = COALESCE(
        NULLIF(ds.data_layer, ''),
        CASE
            WHEN s.schema_name ILIKE 'ads%' THEN 'ADS'
            WHEN s.schema_name ILIKE 'dws%' THEN 'DWS'
            WHEN s.schema_name ILIKE 'dwd%' THEN 'DWD'
            WHEN s.schema_name ILIKE 'dim%' THEN 'DIM'
            WHEN s.schema_name ILIKE 'ods%' THEN 'ODS'
            ELSE 'SOURCE'
        END)
FROM dam_metadata.metadata_schema s
LEFT JOIN dam_metadata.metadata_datasource ds ON ds.id = s.datasource_id
WHERE t.schema_id = s.id
  AND (t.layer IS NULL OR t.layer = '');

-- 2.1 表节点层级：按“表名 → 数据源层级”回填（源库表名不需要前缀）
UPDATE dam_metadata.metadata_lineage_source ls
SET layer = m.layer
FROM (
    SELECT DISTINCT ON (t.table_name)
           t.table_name,
           COALESCE(NULLIF(ds.data_layer, ''),
               CASE
                   WHEN ms.schema_name ILIKE 'ads%' THEN 'ADS'
                   WHEN ms.schema_name ILIKE 'dws%' THEN 'DWS'
                   WHEN ms.schema_name ILIKE 'dwd%' THEN 'DWD'
                   WHEN ms.schema_name ILIKE 'dim%' THEN 'DIM'
                   WHEN ms.schema_name ILIKE 'ods%' THEN 'ODS'
                   ELSE 'SOURCE'
               END) AS layer
      FROM dam_metadata.metadata_table t
      LEFT JOIN dam_metadata.metadata_schema ms ON ms.id = t.schema_id
      LEFT JOIN dam_metadata.metadata_datasource ds ON ds.id = t.datasource_id
     WHERE t.status = 1
     ORDER BY t.table_name, t.id
) m
WHERE ls.source_code = m.table_name
  AND (ls.layer IS NULL OR ls.layer = '')
  AND UPPER(COALESCE(ls.source_type_id, '')) <> 'REPORT';

-- 2.2 剩余节点（报表/未匹配到表的节点）：报表固定 REPORT，其余按编码前缀兜底
UPDATE dam_metadata.metadata_lineage_source
SET layer = CASE
        WHEN UPPER(COALESCE(source_type_id, '')) = 'REPORT' THEN 'REPORT'
        WHEN LOWER(source_code) LIKE 'ads\_%' ESCAPE '\' THEN 'ADS'
        WHEN LOWER(source_code) LIKE 'dws\_%' ESCAPE '\' THEN 'DWS'
        WHEN LOWER(source_code) LIKE 'dwd\_%' ESCAPE '\' THEN 'DWD'
        WHEN LOWER(source_code) LIKE 'dim\_%' ESCAPE '\' THEN 'DIM'
        WHEN LOWER(source_code) LIKE 'dict\_%' ESCAPE '\' THEN 'DIM'
        WHEN LOWER(source_code) LIKE 'ods\_%' ESCAPE '\' THEN 'ODS'
        ELSE 'SOURCE'
    END
WHERE layer IS NULL OR layer = '';

-- 3. 分层边回填：上下游层级与跨层标记
UPDATE dam_metadata.metadata_lineage_hierarchy h
SET upstream_layer = us.layer,
    downstream_layer = ds.layer,
    is_cross_layer = (us.layer IS DISTINCT FROM ds.layer)
FROM dam_metadata.metadata_lineage_source us,
     dam_metadata.metadata_lineage_source ds
WHERE h.upstream_source_id = us.id
  AND h.downstream_source_id = ds.id;

-- 4. 索引：层级筛选与跨层查询
CREATE INDEX IF NOT EXISTS idx_metadata_table_layer ON dam_metadata.metadata_table (layer);
CREATE INDEX IF NOT EXISTS idx_lineage_source_layer ON dam_metadata.metadata_lineage_source (layer);
CREATE INDEX IF NOT EXISTS idx_lineage_hierarchy_cross ON dam_metadata.metadata_lineage_hierarchy (is_cross_layer);

-- 5. 校验：查看各层节点/表数量
-- SELECT layer, COUNT(*) FROM dam_metadata.metadata_table GROUP BY layer ORDER BY layer;
-- SELECT layer, COUNT(*) FROM dam_metadata.metadata_lineage_source GROUP BY layer ORDER BY layer;
-- SELECT is_cross_layer, COUNT(*) FROM dam_metadata.metadata_lineage_hierarchy GROUP BY is_cross_layer;
