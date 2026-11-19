-- v40: 指标定义、质量规则补 department，用于角色数据范围过滤
BEGIN;

ALTER TABLE IF EXISTS dam_metric.metric_definition
    ADD COLUMN IF NOT EXISTS department varchar(100);
COMMENT ON COLUMN dam_metric.metric_definition.department
    IS '指标归属部门；为空表示全院/公共指标';

ALTER TABLE IF EXISTS dam_quality.metadata_quality_rule
    ADD COLUMN IF NOT EXISTS department varchar(100);
COMMENT ON COLUMN dam_quality.metadata_quality_rule.department
    IS '质量规则归属部门；为空表示全院/公共规则';

CREATE INDEX IF NOT EXISTS idx_metric_definition_department
    ON dam_metric.metric_definition (department);
CREATE INDEX IF NOT EXISTS idx_quality_rule_department
    ON dam_quality.metadata_quality_rule (department);

COMMIT;
