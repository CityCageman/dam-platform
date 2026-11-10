-- v15: 知识条目结构化字段
ALTER TABLE dam_asset.knowledge_entry
    ADD COLUMN IF NOT EXISTS entry_type varchar(20) NOT NULL DEFAULT 'TERM',
    ADD COLUMN IF NOT EXISTS definition text,
    ADD COLUMN IF NOT EXISTS usage_note text,
    ADD COLUMN IF NOT EXISTS aliases jsonb,
    ADD COLUMN IF NOT EXISTS field_mappings jsonb,
    ADD COLUMN IF NOT EXISTS related_metrics jsonb,
    ADD COLUMN IF NOT EXISTS source_system varchar(100),
    ADD COLUMN IF NOT EXISTS owner varchar(100),
    ADD COLUMN IF NOT EXISTS publish_status varchar(20) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS effective_from timestamp,
    ADD COLUMN IF NOT EXISTS effective_to timestamp;

CREATE INDEX IF NOT EXISTS idx_knowledge_entry_type
    ON dam_asset.knowledge_entry (entry_type, publish_status);
