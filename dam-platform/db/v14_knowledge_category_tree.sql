-- v14: 知识分类树 + WeKnora 路径标签
ALTER TABLE dam_asset.knowledge_category
    ADD COLUMN IF NOT EXISTS parent_id bigint,
    ADD COLUMN IF NOT EXISTS category_path varchar(500),
    ADD COLUMN IF NOT EXISTS category_level integer NOT NULL DEFAULT 1;

UPDATE dam_asset.knowledge_category
SET category_path = category_name
WHERE category_path IS NULL OR category_path = '';

UPDATE dam_asset.knowledge_category
SET category_level = 1
WHERE category_level IS NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_category_parent
    ON dam_asset.knowledge_category (parent_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_category_path
    ON dam_asset.knowledge_category (category_path);
