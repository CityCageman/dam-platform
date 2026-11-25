-- DGP 产品数据库迁移：dam_dgp
BEGIN;
CREATE SCHEMA IF NOT EXISTS dam_dgp;
DO $$
DECLARE t record; pair text[]; moves text[][] := ARRAY[
  ['dam_standard','dam_dgp'],
  ['dam_quality','dam_dgp'],
  ['dam_security','dam_dgp'],
  ['dam_lifecycle','dam_dgp'],
  ['dam_lineage','dam_dgp']
];
BEGIN
  FOREACH pair SLICE 1 IN ARRAY moves LOOP
    -- 报表维度和报表目录归属 DAN，DAM 产品包不得把它们搬进 dam_dam
    FOR t IN SELECT tablename FROM pg_tables
      WHERE schemaname=pair[1]
        AND NOT (pair[1]='dam_asset' AND tablename IN
                 ('report_dimension','report_dimension_item','report_catalog'))
    LOOP
      EXECUTE format('ALTER TABLE %I.%I SET SCHEMA %I', pair[1], t.tablename, pair[2]);
    END LOOP;
  END LOOP;
END $$;
-- 迁移序列
DO $$
DECLARE s record; pair text[]; moves text[][] := ARRAY[
  ['dam_standard','dam_dgp'],
  ['dam_quality','dam_dgp'],
  ['dam_security','dam_dgp'],
  ['dam_lifecycle','dam_dgp'],
  ['dam_lineage','dam_dgp']
];
BEGIN
  FOREACH pair SLICE 1 IN ARRAY moves LOOP
    FOR s IN SELECT sequence_name FROM information_schema.sequences
      WHERE sequence_schema=pair[1]
    LOOP
      IF to_regclass(format('%I.%I', pair[2], s.sequence_name)) IS NULL THEN
        EXECUTE format('ALTER SEQUENCE %I.%I SET SCHEMA %I', pair[1], s.sequence_name, pair[2]);
      END IF;
    END LOOP;
  END LOOP;
END $$;
COMMIT;
