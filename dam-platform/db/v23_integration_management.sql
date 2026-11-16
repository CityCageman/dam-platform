-- v23: 数据集成任务管理
-- 说明：本脚本只创建集成任务的持久化结构，不包含同步执行引擎、调度记录或同步明细表。
CREATE SCHEMA IF NOT EXISTS dam_integration;

CREATE TABLE IF NOT EXISTS dam_integration.integration_job (
    id            BIGSERIAL    PRIMARY KEY,
    job_code      VARCHAR(64)  NOT NULL,
    job_name      VARCHAR(128) NOT NULL,
    source_type   VARCHAR(32)  NOT NULL,
    source_config JSONB,
    target_type   VARCHAR(32)  NOT NULL,
    target_config JSONB,
    sync_mode     VARCHAR(32),
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    last_run_at   TIMESTAMP,
    last_error    TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    created_by    VARCHAR(64),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by    VARCHAR(64),
    deleted_at    TIMESTAMP,
    CONSTRAINT ck_integration_job_status
        CHECK (status IN ('DRAFT', 'ENABLED', 'DISABLED', 'RUNNING', 'FAILED'))
);

COMMENT ON TABLE dam_integration.integration_job IS '数据集成任务定义（仅管理任务配置与状态，不执行真实同步）';
COMMENT ON COLUMN dam_integration.integration_job.job_code IS '任务编码，全局唯一';
COMMENT ON COLUMN dam_integration.integration_job.source_config IS '源端连接配置（JSONB，结构由 source_type 决定）';
COMMENT ON COLUMN dam_integration.integration_job.target_config IS '目标端连接配置（JSONB，结构由 target_type 决定）';
COMMENT ON COLUMN dam_integration.integration_job.status IS '任务状态：DRAFT/ENABLED/DISABLED/RUNNING/FAILED';
COMMENT ON COLUMN dam_integration.integration_job.deleted_at IS '逻辑删除时间，NULL 表示未删除';

-- job_code 唯一索引（同时作为 code 查询索引）
CREATE UNIQUE INDEX IF NOT EXISTS idx_integration_job_code
    ON dam_integration.integration_job (job_code);

CREATE INDEX IF NOT EXISTS idx_integration_job_status
    ON dam_integration.integration_job (status);
