-- =====================================================================
-- v12：知识库迁移到 WeKnora
-- 一个外部知识库 + 本地分类映射为外部 tag
-- =====================================================================
ALTER TABLE dam_asset.knowledge_category
    ADD COLUMN IF NOT EXISTS external_tag_id varchar(100);

ALTER TABLE dam_asset.knowledge_entry
    ADD COLUMN IF NOT EXISTS external_kb_id varchar(100),
    ADD COLUMN IF NOT EXISTS external_doc_id varchar(100),
    ADD COLUMN IF NOT EXISTS sync_status varchar(20) NOT NULL DEFAULT 'LOCAL_ONLY',
    ADD COLUMN IF NOT EXISTS last_synced_at timestamp,
    ADD COLUMN IF NOT EXISTS last_error varchar(1000);

CREATE INDEX IF NOT EXISTS idx_knowledge_entry_sync
    ON dam_asset.knowledge_entry (sync_status, external_doc_id);

CREATE TABLE IF NOT EXISTS dam_asset.knowledge_sync_config (
    id                bigint PRIMARY KEY,
    external_kb_id    varchar(100),
    external_kb_name  varchar(200),
    auto_sync         smallint NOT NULL DEFAULT 0,
    status            smallint NOT NULL DEFAULT 1,
    created_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        varchar(100) NOT NULL DEFAULT 'system',
    updated_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        varchar(100) NOT NULL DEFAULT 'system'
);
INSERT INTO dam_asset.knowledge_sync_config (id, auto_sync, status, created_by, updated_by)
SELECT 1, 0, 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_asset.knowledge_sync_config WHERE id = 1);
