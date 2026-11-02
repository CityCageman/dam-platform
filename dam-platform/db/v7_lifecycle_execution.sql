-- =====================================================================
-- v7：生命周期执行记录（可选，用于归档/销毁/通知追溯）
-- 不改旧表；仅新增执行日志表。执行前请先备份。
-- =====================================================================
CREATE TABLE IF NOT EXISTS dam_metadata.metadata_lifecycle_execution (
    id            bigserial PRIMARY KEY,
    policy_id     bigint,
    policy_name   varchar(200),
    asset_id      bigint,
    asset_name    varchar(300),
    tier          varchar(50),
    action        varchar(30),
    reason        varchar(500),
    executed_at   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_by   varchar(100) NOT NULL DEFAULT 'system'
);
COMMENT ON TABLE dam_metadata.metadata_lifecycle_execution IS '生命周期策略执行记录（归档/销毁/通知）';
CREATE INDEX IF NOT EXISTS idx_lifecycle_exec_policy ON dam_metadata.metadata_lifecycle_execution (policy_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_exec_time ON dam_metadata.metadata_lifecycle_execution (executed_at);
