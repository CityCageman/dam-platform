-- v44: 产品级 schema 拆分
-- 目标：每个产品一个 schema；报表维度独立到 dam_dimension
-- 执行前必须备份；本脚本只移动表/序列，不删除旧 schema，便于回滚和兼容视图。

BEGIN;

CREATE SCHEMA IF NOT EXISTS dam_dam;
CREATE SCHEMA IF NOT EXISTS dam_dpm;
CREATE SCHEMA IF NOT EXISTS dam_dap;
CREATE SCHEMA IF NOT EXISTS dam_dgp;
CREATE SCHEMA IF NOT EXISTS dam_dpp;
CREATE SCHEMA IF NOT EXISTS dam_dsp;
CREATE SCHEMA IF NOT EXISTS dam_dan;
CREATE SCHEMA IF NOT EXISTS dam_dapp;
CREATE SCHEMA IF NOT EXISTS dam_ai;
CREATE SCHEMA IF NOT EXISTS dam_dimension;

-- 1. 报表维度先进入独立维度库
DO $$
DECLARE t record;
BEGIN
  FOR t IN
    SELECT tablename FROM pg_tables
    WHERE schemaname = 'dam_asset'
      AND tablename IN ('report_dimension','report_dimension_item')
  LOOP
    EXECUTE format('ALTER TABLE dam_asset.%I SET SCHEMA dam_dimension', t.tablename);
  END LOOP;
END $$;

-- 2. 各产品表迁移
DO $$
DECLARE
  t record;
  pair text[];
  moves text[][] := ARRAY[
    ['dam_asset',        'dam_dam'],
    ['dam_metadata',     'dam_dam'],
    ['dam_mdm',          'dam_dam'],
    ['dam_model',        'dam_dpp'],
    ['dam_dev',          'dam_dpp'],
    ['dam_sys',          'dam_dpm'],
    ['dam_ticket',       'dam_dpm'],
    ['dam_task',         'dam_dpm'],
    ['dam_scheduler',    'dam_dpm'],
    ['dam_sched',        'dam_dpm'],
    ['dam_access',       'dam_dap'],
    ['dam_integration',  'dam_dap'],
    ['dam_standard',     'dam_dgp'],
    ['dam_quality',      'dam_dgp'],
    ['dam_security',     'dam_dgp'],
    ['dam_lifecycle',    'dam_dgp'],
    ['dam_lineage',      'dam_dgp'],
    ['dam_data_service', 'dam_dsp'],
    ['dam_metric',       'dam_dan'],
    ['dam_report',       'dam_dan'],
    ['dam_ai',           'dam_ai'],
    ['dam_knowledge',    'dam_ai']
  ];
BEGIN
  FOREACH pair SLICE 1 IN ARRAY moves LOOP
    FOR t IN
      SELECT tablename FROM pg_tables WHERE schemaname = pair[1]
    LOOP
      EXECUTE format('ALTER TABLE %I.%I SET SCHEMA %I', pair[1], t.tablename, pair[2]);
    END LOOP;
  END LOOP;
END $$;

-- 3. 序列迁移（跳过已经随表迁移/目标已存在的序列）
DO $$
DECLARE
  s record;
  pair text[];
  moves text[][] := ARRAY[
    ['dam_asset',        'dam_dam'],
    ['dam_metadata',     'dam_dam'],
    ['dam_mdm',          'dam_dam'],
    ['dam_model',        'dam_dpp'],
    ['dam_dev',          'dam_dpp'],
    ['dam_sys',          'dam_dpm'],
    ['dam_ticket',       'dam_dpm'],
    ['dam_task',         'dam_dpm'],
    ['dam_scheduler',    'dam_dpm'],
    ['dam_sched',        'dam_dpm'],
    ['dam_access',       'dam_dap'],
    ['dam_integration',  'dam_dap'],
    ['dam_standard',     'dam_dgp'],
    ['dam_quality',      'dam_dgp'],
    ['dam_security',     'dam_dgp'],
    ['dam_lifecycle',    'dam_dgp'],
    ['dam_lineage',      'dam_dgp'],
    ['dam_data_service', 'dam_dsp'],
    ['dam_metric',       'dam_dan'],
    ['dam_report',       'dam_dan'],
    ['dam_ai',           'dam_ai'],
    ['dam_knowledge',    'dam_ai']
  ];
BEGIN
  FOREACH pair SLICE 1 IN ARRAY moves LOOP
    FOR s IN
      SELECT sequence_name FROM information_schema.sequences
      WHERE sequence_schema = pair[1]
    LOOP
      IF to_regclass(format('%I.%I', pair[2], s.sequence_name)) IS NULL THEN
        EXECUTE format('ALTER SEQUENCE %I.%I SET SCHEMA %I', pair[1], s.sequence_name, pair[2]);
      END IF;
    END LOOP;
  END LOOP;
END $$;

COMMIT;
