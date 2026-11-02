-- =====================================================================
-- v6 血缘节点 schema 化：增量迁移（只做加法，不删不改旧字段/旧 ID）
-- ---------------------------------------------------------------------
-- 兼容性承诺：
--   1. 不修改 source_code、id、hierarchy、metadata_lineage、column_lineage；
--   2. 只新增 node_key 列并回填，旧应用不读该列，功能不受影响；
--   3. 不合并重复节点、不建唯一索引，避免误删/误合并；
--   4. 回填按批提交，索引 CONCURRENTLY，尽量降低锁影响。
--
-- 执行方式：
--   psql -h <host> -p <port> -U dam -d damplatform -v ON_ERROR_STOP=1 \
--        -f db/v6_lineage_identity.sql
--   注意：不要在 BEGIN/COMMIT 事务块中执行本文件（CALL 内部需要提交，
--         CREATE INDEX CONCURRENTLY 也不能在事务块中运行）。
-- =====================================================================

SET lock_timeout = '5s';

DO $$
BEGIN
    IF to_regclass('dam_metadata.metadata_lineage_source') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE dam_metadata.metadata_lineage_source ADD COLUMN IF NOT EXISTS node_key text';
    END IF;
END $$;

RESET lock_timeout;

-- ---------------------------------------------------------------------
-- 分批回填：先补 schema/数据源/表 ID，再补 node_key
-- ---------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE dam_metadata.v6_backfill_lineage_identity(p_batch integer DEFAULT 5000)
LANGUAGE plpgsql
AS $$
DECLARE
    v_rows integer;
BEGIN
    -- 只映射“表名在全库唯一”的表，同名跨 Schema 的表保持不猜测
    DROP TABLE IF EXISTS pg_temp.tmp_v6_node_map;
    CREATE TEMP TABLE tmp_v6_node_map ON COMMIT PRESERVE ROWS AS
    SELECT DISTINCT ON (lower(t.table_name))
           lower(t.table_name) AS bare_key,
           lower(ms.schema_name) || '.' || lower(t.table_name) AS full_key,
           t.table_name,
           t.id AS table_id,
           t.datasource_id,
           ms.schema_name
    FROM dam_metadata.metadata_table t
    JOIN dam_metadata.metadata_schema ms ON ms.id = t.schema_id
    WHERE t.status = 1
      AND lower(t.table_name) IN (
          SELECT lower(t2.table_name)
          FROM dam_metadata.metadata_table t2
          WHERE t2.status = 1
          GROUP BY lower(t2.table_name)
          HAVING count(DISTINCT t2.schema_id) = 1
      )
    ORDER BY lower(t.table_name), t.id;

    LOOP
        WITH batch AS (
            SELECT ls.id
            FROM dam_metadata.metadata_lineage_source ls
            JOIN tmp_v6_node_map m
              ON lower(ls.source_code) = m.bare_key
              OR lower(ls.source_code) = m.full_key
            WHERE ls.table_id IS NULL
              AND UPPER(COALESCE(ls.source_type_id, '')) = 'TABLE'
            LIMIT p_batch
        )
        UPDATE dam_metadata.metadata_lineage_source ls
        SET schema_name = m.schema_name,
            datasource_id = m.datasource_id,
            table_id = m.table_id
        FROM batch b, tmp_v6_node_map m
        WHERE ls.id = b.id
          AND (lower(ls.source_code) = m.bare_key OR lower(ls.source_code) = m.full_key);

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN v_rows = 0;
        PERFORM pg_sleep(0.05);
    END LOOP;

    LOOP
        WITH batch AS (
            SELECT id
            FROM dam_metadata.metadata_lineage_source
            WHERE node_key IS NULL OR node_key = ''
            LIMIT p_batch
        )
        UPDATE dam_metadata.metadata_lineage_source ls
        SET node_key = lower(
            CASE
                WHEN ls.source_code IS NULL OR ls.source_code = ''
                    THEN 'node:' || ls.id
                WHEN ls.schema_name IS NOT NULL AND ls.schema_name <> ''
                     AND lower(left(ls.source_code, length(ls.schema_name) + 1))
                         <> lower(ls.schema_name) || '.'
                    THEN ls.schema_name || '.' || ls.source_code
                ELSE ls.source_code
            END)
        FROM batch b
        WHERE ls.id = b.id;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        COMMIT;
        EXIT WHEN v_rows = 0;
        PERFORM pg_sleep(0.05);
    END LOOP;

    DROP TABLE IF EXISTS pg_temp.tmp_v6_node_map;
END;
$$;

CALL dam_metadata.v6_backfill_lineage_identity(5000);

DROP PROCEDURE IF EXISTS dam_metadata.v6_backfill_lineage_identity(integer);

-- ---------------------------------------------------------------------
-- 并发索引：不阻塞正常读写
-- ---------------------------------------------------------------------
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_lineage_source_node_key
    ON dam_metadata.metadata_lineage_source (node_key);

-- ---------------------------------------------------------------------
-- 重复审计视图：唯一索引前必须先确认此视图为空
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW dam_metadata.v_lineage_node_dup AS
SELECT node_key,
       count(*) AS cnt,
       array_agg(id ORDER BY id) AS ids
FROM dam_metadata.metadata_lineage_source
GROUP BY node_key
HAVING count(*) > 1;

-- 验证
SELECT count(*) AS total_nodes,
       count(*) FILTER (WHERE node_key IS NOT NULL) AS keyed_nodes,
       count(*) FILTER (WHERE table_id IS NOT NULL) AS mapped_nodes
FROM dam_metadata.metadata_lineage_source;

SELECT * FROM dam_metadata.v_lineage_node_dup LIMIT 20;

-- =====================================================================
-- 唯一索引暂不创建。确认 v_lineage_node_dup 为空（或重复已人工处理后）
-- 再单独执行：
-- CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_lineage_source_node_key
--     ON dam_metadata.metadata_lineage_source (node_key);
-- =====================================================================
