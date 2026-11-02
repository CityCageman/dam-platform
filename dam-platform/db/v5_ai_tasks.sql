-- =====================================================================
-- v5：AI 任务中心（任务 + 明细草稿）
-- 说明：任务中心统一承载 AI 注释、分类、血缘、报表等生成结果，
--       明细先落草稿（DRAFT），人工采纳/驳回后回写业务表。
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS dam_asset;

CREATE TABLE IF NOT EXISTS dam_asset.ai_task (
    id              bigserial PRIMARY KEY,
    task_type       varchar(32)  NOT NULL,
    title           varchar(200) NOT NULL,
    target_type     varchar(32),
    target_id       bigint,
    status          varchar(16)  NOT NULL DEFAULT 'PENDING',
    total_count     integer      NOT NULL DEFAULT 0,
    accepted_count  integer      NOT NULL DEFAULT 0,
    rejected_count  integer      NOT NULL DEFAULT 0,
    model           varchar(100),
    prompt          text,
    error_message   varchar(500),
    created_by      varchar(100) NOT NULL DEFAULT 'system',
    created_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE dam_asset.ai_task IS 'AI 任务中心-任务';
CREATE INDEX IF NOT EXISTS idx_ai_task_status ON dam_asset.ai_task (status);
CREATE INDEX IF NOT EXISTS idx_ai_task_type ON dam_asset.ai_task (task_type);

CREATE TABLE IF NOT EXISTS dam_asset.ai_task_item (
    id              bigserial PRIMARY KEY,
    task_id         bigint       NOT NULL,
    item_type       varchar(32),
    target_id       bigint,
    target_name     varchar(200),
    field_name      varchar(100),
    draft_content   text,
    confidence      numeric(5, 2),
    status          varchar(16)  NOT NULL DEFAULT 'DRAFT',
    reviewed_by     varchar(100),
    reviewed_at     timestamp,
    created_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE dam_asset.ai_task_item IS 'AI 任务中心-明细草稿';
CREATE INDEX IF NOT EXISTS idx_ai_task_item_task ON dam_asset.ai_task_item (task_id);
CREATE INDEX IF NOT EXISTS idx_ai_task_item_status ON dam_asset.ai_task_item (status);
