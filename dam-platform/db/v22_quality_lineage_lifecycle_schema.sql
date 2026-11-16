-- v22: 质量、血缘、生命周期表从 dam_metadata 迁出
CREATE SCHEMA IF NOT EXISTS dam_quality;
CREATE SCHEMA IF NOT EXISTS dam_lineage;
CREATE SCHEMA IF NOT EXISTS dam_lifecycle;

ALTER TABLE IF EXISTS dam_metadata.metadata_quality_rule SET SCHEMA dam_quality;
ALTER TABLE IF EXISTS dam_metadata.metadata_quality_check_result SET SCHEMA dam_quality;

ALTER TABLE IF EXISTS dam_metadata.metadata_lineage SET SCHEMA dam_lineage;
ALTER TABLE IF EXISTS dam_metadata.metadata_column_lineage SET SCHEMA dam_lineage;
ALTER TABLE IF EXISTS dam_metadata.metadata_lineage_hierarchy SET SCHEMA dam_lineage;
ALTER TABLE IF EXISTS dam_metadata.metadata_lineage_source SET SCHEMA dam_lineage;
ALTER TABLE IF EXISTS dam_metadata.metadata_report_sql_parser SET SCHEMA dam_lineage;
ALTER TABLE IF EXISTS dam_metadata.metadata_column_calc_dependency SET SCHEMA dam_lineage;

ALTER TABLE IF EXISTS dam_metadata.metadata_lifecycle_policy SET SCHEMA dam_lifecycle;
ALTER TABLE IF EXISTS dam_metadata.metadata_lifecycle_execution SET SCHEMA dam_lifecycle;
ALTER TABLE IF EXISTS dam_metadata.metadata_destruction_record SET SCHEMA dam_lifecycle;

DO $$
DECLARE seq record;
BEGIN
    FOR seq IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'dam_metadata'
          AND c.relkind = 'S'
    LOOP
        IF seq.relname LIKE 'metadata_quality_%' THEN
            EXECUTE format('ALTER SEQUENCE dam_metadata.%I SET SCHEMA dam_quality', seq.relname);
        ELSIF seq.relname LIKE 'metadata_lifecycle_%' OR seq.relname LIKE 'metadata_destruction_%' THEN
            EXECUTE format('ALTER SEQUENCE dam_metadata.%I SET SCHEMA dam_lifecycle', seq.relname);
        ELSIF seq.relname LIKE 'metadata_lineage_%'
           OR seq.relname LIKE 'metadata_column_lineage%'
           OR seq.relname LIKE 'metadata_column_calc_dependency%'
           OR seq.relname LIKE 'metadata_report_sql_parser%' THEN
            EXECUTE format('ALTER SEQUENCE dam_metadata.%I SET SCHEMA dam_lineage', seq.relname);
        END IF;
    END LOOP;
END $$;
