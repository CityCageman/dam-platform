-- v20: 知识库指标解释关联指标库，计算口径以指标库为唯一来源
ALTER TABLE dam_asset.knowledge_entry
    ADD COLUMN IF NOT EXISTS metric_id bigint;

ALTER TABLE dam_asset.metric_definition
    ADD COLUMN IF NOT EXISTS report_spec jsonb;

CREATE INDEX IF NOT EXISTS idx_knowledge_entry_metric
    ON dam_asset.knowledge_entry (metric_id);

-- 回填历史 METRIC 知识条目：source_key = metric:<id>
UPDATE dam_asset.knowledge_entry
SET metric_id = substring(source_key from 'metric:([0-9]+)')::bigint
WHERE metric_id IS NULL
  AND source_key ~ '^metric:[0-9]+$';

-- 把知识库中已有的可执行规则/参考 SQL 一次性迁到指标库（仅在指标库为空时）
UPDATE dam_asset.metric_definition m
SET report_spec = k.report_spec
FROM dam_asset.knowledge_entry k
WHERE k.metric_id = m.id
  AND m.report_spec IS NULL
  AND k.report_spec IS NOT NULL;

UPDATE dam_asset.metric_definition m
SET sql_expression = k.sql_example
FROM dam_asset.knowledge_entry k
WHERE k.metric_id = m.id
  AND (m.sql_expression IS NULL OR m.sql_expression = '')
  AND k.sql_example IS NOT NULL;
