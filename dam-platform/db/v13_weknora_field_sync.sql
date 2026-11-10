-- v13: WeKnora external tag/knowledge fields backfill
ALTER TABLE dam_asset.knowledge_category
    ADD COLUMN IF NOT EXISTS external_tag_name varchar(200),
    ADD COLUMN IF NOT EXISTS external_tag_color varchar(50);

ALTER TABLE dam_asset.knowledge_entry
    ADD COLUMN IF NOT EXISTS external_summary text,
    ADD COLUMN IF NOT EXISTS external_metadata jsonb,
    ADD COLUMN IF NOT EXISTS external_tags jsonb,
    ADD COLUMN IF NOT EXISTS external_parse_status varchar(30),
    ADD COLUMN IF NOT EXISTS external_updated_at timestamp;
