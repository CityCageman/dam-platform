-- =====================================================================
-- v37：数据源归属迁移到 dam_access（数据接入服务 dam-integration）
--
-- 背景：
--   数据源管理（含连接测试）的写入口从 dam-catalog 迁移到 dam-integration。
--   数据源表物理归属到 dam_access schema；dam_metadata.metadata_datasource
--   保留为只读兼容视图，供 DAM 元数据采集、报表、血缘等既有读取方使用。
--
-- 幂等与安全：
--   1. 仅当 dam_metadata.metadata_datasource 仍是表时才移动到 dam_access；
--      移动表时索引、约束、自增序列随表一起移动。
--   2. 仅当 dam_access.metadata_datasource 不存在时才建表；已有表/数据不重建、
--      不截断、不删除。
--   3. 目标已存在且源表仍存在（异常半迁移状态）时主动报错，避免静默丢数据。
--   4. 视图使用 CREATE OR REPLACE，可重复执行，不删除视图。
--   5. 存量表没有 data_layer 列时不强制新增（新建表时才包含该列）。
--
-- 执行方式：psql -v ON_ERROR_STOP=1 -f db/v37_data_access.sql
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS dam_access;

DO $v37$
DECLARE
    source_kind text;
    target_kind text;
    create_table_sql text := $ddl$
        CREATE TABLE dam_access.metadata_datasource (
            id               bigserial PRIMARY KEY,
            ds_name          varchar(100) NOT NULL,
            ds_code          varchar(100) NOT NULL,
            ds_type          varchar(50)  NOT NULL,
            ds_adapter       varchar(100) NOT NULL,
            ds_ip            varchar(20),
            ds_port          varchar(10),
            jdbc_url         varchar(200) NOT NULL,
            username         varchar(100) NOT NULL,
            password         varchar(100) NOT NULL,
            driver_class     varchar(200) NOT NULL,
            connection_props varchar(200),
            description      varchar(200),
            status           smallint     NOT NULL DEFAULT 1,
            data_layer       varchar(16),
            created_at       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
            created_by       varchar(100) NOT NULL DEFAULT '系统',
            updated_at       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_by       varchar(100) NOT NULL DEFAULT '系统',
            deleted_at       timestamp
        )
    $ddl$;
BEGIN
    SELECT c.relkind::text
      INTO source_kind
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'dam_metadata'
       AND c.relname = 'metadata_datasource';

    SELECT c.relkind::text
      INTO target_kind
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'dam_access'
       AND c.relname = 'metadata_datasource';

    IF source_kind IN ('r', 'p') THEN
        -- 正常迁移路径：dam_metadata 下的物理表整体移动到 dam_access。
        IF target_kind IS NULL THEN
            EXECUTE 'ALTER TABLE dam_metadata.metadata_datasource SET SCHEMA dam_access';
            RAISE NOTICE 'v37: 已将 dam_metadata.metadata_datasource 迁移到 dam_access';
        ELSE
            -- 两个 schema 同时存在物理表：不做猜测性合并，直接失败，交由人工核对。
            RAISE EXCEPTION
                'v37: dam_metadata.metadata_datasource 与 dam_access.metadata_datasource 同时存在，为避免数据丢失已中止；请人工核对后保留其中一份再重跑本脚本';
        END IF;
    ELSIF source_kind IS NULL OR source_kind = 'v' THEN
        -- 首次部署（不存在）或已迁移为视图：确保 dam_access 下有物理表。
        IF target_kind IS NULL THEN
            EXECUTE create_table_sql;
            EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS idx_metadata_datasource_code '
                 || 'ON dam_access.metadata_datasource(ds_code) WHERE deleted_at IS NULL';
            RAISE NOTICE 'v37: 已创建 dam_access.metadata_datasource';
        END IF;
    ELSE
        RAISE EXCEPTION
            'v37: dam_metadata.metadata_datasource 是未预期的关系类型(%)，已中止', source_kind;
    END IF;
END;
$v37$;

-- 只读兼容视图：业务读取方仍可使用 dam_metadata.metadata_datasource。
-- 使用 CREATE OR REPLACE，重复执行不会删除视图及其授权。
CREATE OR REPLACE VIEW dam_metadata.metadata_datasource AS
SELECT * FROM dam_access.metadata_datasource;

COMMENT ON SCHEMA dam_access IS '数据接入域：数据源连接等可写数据';
COMMENT ON TABLE dam_access.metadata_datasource IS '数据源表（写入口：dam-integration /api/access/data-sources）';
COMMENT ON VIEW dam_metadata.metadata_datasource IS '数据源只读兼容视图，指向 dam_access.metadata_datasource；写操作请走 dam-integration';
