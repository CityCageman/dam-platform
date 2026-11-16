-- v21: 指标、报表、知识从 dam_asset 迁出，建立独立数据边界
CREATE SCHEMA IF NOT EXISTS dam_metric;
CREATE SCHEMA IF NOT EXISTS dam_report;
CREATE SCHEMA IF NOT EXISTS dam_knowledge;

ALTER TABLE IF EXISTS dam_asset.metric_definition SET SCHEMA dam_metric;
ALTER TABLE IF EXISTS dam_asset.metric_value SET SCHEMA dam_metric;
ALTER TABLE IF EXISTS dam_asset.report_catalog SET SCHEMA dam_report;
ALTER TABLE IF EXISTS dam_asset.knowledge_category SET SCHEMA dam_knowledge;
ALTER TABLE IF EXISTS dam_asset.knowledge_entry SET SCHEMA dam_knowledge;
ALTER TABLE IF EXISTS dam_asset.knowledge_sync_config SET SCHEMA dam_knowledge;

-- serial/identity 序列迁移
DO $$
DECLARE seq record;
BEGIN
    FOR seq IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'dam_asset'
          AND c.relkind = 'S'
          AND (c.relname LIKE 'metric_definition%'
               OR c.relname LIKE 'metric_value%'
               OR c.relname LIKE 'report_catalog%'
               OR c.relname LIKE 'knowledge_category%'
               OR c.relname LIKE 'knowledge_entry%'
               OR c.relname LIKE 'knowledge_sync_config%')
    LOOP
        IF seq.relname LIKE 'report_catalog%' THEN
            EXECUTE format('ALTER SEQUENCE dam_asset.%I SET SCHEMA dam_report', seq.relname);
        ELSIF seq.relname LIKE 'knowledge_%' THEN
            EXECUTE format('ALTER SEQUENCE dam_asset.%I SET SCHEMA dam_knowledge', seq.relname);
        ELSE
            EXECUTE format('ALTER SEQUENCE dam_asset.%I SET SCHEMA dam_metric', seq.relname);
        END IF;
    END LOOP;
END $$;
