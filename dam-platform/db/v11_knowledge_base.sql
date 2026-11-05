-- =====================================================================
-- v11：知识库（可维护分类 + 条目 + 向量索引状态）
-- =====================================================================
CREATE TABLE IF NOT EXISTS dam_asset.knowledge_category (
    id              bigserial PRIMARY KEY,
    category_code   varchar(100) NOT NULL,
    category_name   varchar(200) NOT NULL,
    description     varchar(500),
    sort_order      integer NOT NULL DEFAULT 0,
    status          smallint NOT NULL DEFAULT 1,
    created_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      varchar(100) NOT NULL DEFAULT 'system',
    updated_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      varchar(100) NOT NULL DEFAULT 'system',
    deleted_at      timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_category_code
    ON dam_asset.knowledge_category (category_code) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS dam_asset.knowledge_entry (
    id                bigserial PRIMARY KEY,
    category_id       bigint NOT NULL,
    title             varchar(300) NOT NULL,
    content           text,
    source_type       varchar(30) NOT NULL DEFAULT 'MANUAL',
    source_id         varchar(100),
    source_key        varchar(300),
    tags              jsonb,
    vector_id         varchar(200),
    embedding_status  varchar(20) NOT NULL DEFAULT 'PENDING',
    enabled           smallint NOT NULL DEFAULT 1,
    created_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        varchar(100) NOT NULL DEFAULT 'system',
    updated_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        varchar(100) NOT NULL DEFAULT 'system',
    deleted_at        timestamp
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_entry_source
    ON dam_asset.knowledge_entry (source_type, source_key) WHERE deleted_at IS NULL AND source_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_knowledge_entry_category
    ON dam_asset.knowledge_entry (category_id, enabled);
CREATE INDEX IF NOT EXISTS idx_knowledge_entry_embedding
    ON dam_asset.knowledge_entry (embedding_status);

INSERT INTO dam_asset.knowledge_category
    (category_code, category_name, description, sort_order, status, created_by, updated_by)
SELECT v.code, v.name, v.descr, v.sort, 1, 'system', 'system'
FROM (VALUES
    ('DOMAIN_GLOSSARY', '领域词典', '表名、字段名、业务术语的统一解释', 1),
    ('METRIC_DEFINITION', '指标口径', '指标定义、计算公式和统计口径', 2),
    ('STANDARD_TERM', '标准术语', '数据标准、码表与标准代码值', 3),
    ('HISTORY_CASE', '历史案例', '已确认的查询/报表案例与结论', 4)
) AS v(code, name, descr, sort)
WHERE NOT EXISTS (
    SELECT 1 FROM dam_asset.knowledge_category c
    WHERE c.category_code = v.code AND c.deleted_at IS NULL
);
