-- v18: 知识条目取数规则（供 AI 报表按业务口径生成 SQL）
ALTER TABLE dam_asset.knowledge_entry
    ADD COLUMN IF NOT EXISTS report_spec jsonb;
