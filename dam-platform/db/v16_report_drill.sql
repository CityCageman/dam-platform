-- v16: 报表下钻 SQL
ALTER TABLE dam_asset.report_catalog
    ADD COLUMN IF NOT EXISTS drill_sql text;
