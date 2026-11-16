-- v24: 调度任务管理（dam-scheduler 独立 schema）
-- 说明：仅持久化调度任务元数据与触发记录，不包含真实 DAG 执行引擎；
--       触发接口只写入 PENDING 运行记录，由后续执行引擎接管。
CREATE SCHEMA IF NOT EXISTS dam_scheduler;

-- 调度任务定义
CREATE TABLE IF NOT EXISTS dam_scheduler.schedule_job (
    id              BIGSERIAL PRIMARY KEY,
    job_code        VARCHAR(64)  NOT NULL,
    job_name        VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    dag_code        VARCHAR(64),
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    deleted_at      TIMESTAMP,
    CONSTRAINT ck_schedule_job_status
        CHECK (status IN ('DRAFT', 'ENABLED', 'DISABLED'))
);

COMMENT ON TABLE dam_scheduler.schedule_job IS '调度任务定义';
COMMENT ON COLUMN dam_scheduler.schedule_job.job_code IS '任务编码，全局唯一';
COMMENT ON COLUMN dam_scheduler.schedule_job.job_name IS '任务名称';
COMMENT ON COLUMN dam_scheduler.schedule_job.cron_expression IS 'Cron 表达式（Spring 六段式）';
COMMENT ON COLUMN dam_scheduler.schedule_job.dag_code IS '关联 DAG 编码，可为空';
COMMENT ON COLUMN dam_scheduler.schedule_job.status IS '状态：DRAFT/ENABLED/DISABLED';
COMMENT ON COLUMN dam_scheduler.schedule_job.deleted_at IS '逻辑删除时间，NULL 表示未删除';

-- job_code 唯一；status 用于列表筛选
CREATE UNIQUE INDEX IF NOT EXISTS uk_schedule_job_code
    ON dam_scheduler.schedule_job (job_code);
CREATE INDEX IF NOT EXISTS idx_schedule_job_status
    ON dam_scheduler.schedule_job (status);

-- 调度触发/运行记录
CREATE TABLE IF NOT EXISTS dam_scheduler.schedule_run (
    id            BIGSERIAL PRIMARY KEY,
    job_id        BIGINT       NOT NULL,
    run_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    error_message TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_schedule_run_status
        CHECK (run_status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED'))
);

COMMENT ON TABLE dam_scheduler.schedule_run IS '调度任务运行记录';
COMMENT ON COLUMN dam_scheduler.schedule_run.job_id IS '所属调度任务ID';
COMMENT ON COLUMN dam_scheduler.schedule_run.run_status IS '运行状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED';
COMMENT ON COLUMN dam_scheduler.schedule_run.started_at IS '开始执行时间';
COMMENT ON COLUMN dam_scheduler.schedule_run.finished_at IS '结束时间';
COMMENT ON COLUMN dam_scheduler.schedule_run.error_message IS '失败原因';

-- 运行记录按任务与状态检索
CREATE INDEX IF NOT EXISTS idx_schedule_run_job_id
    ON dam_scheduler.schedule_run (job_id);
CREATE INDEX IF NOT EXISTS idx_schedule_run_status
    ON dam_scheduler.schedule_run (run_status);
