-- v17: 知识条目参考 SQL，用于 AI 报表/取数时按业务口径生成 SQL
ALTER TABLE dam_asset.knowledge_entry
    ADD COLUMN IF NOT EXISTS sql_example text;
