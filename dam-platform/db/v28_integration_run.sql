-- v28: 数据集成执行记录
-- 说明：为 dam-integration 的真实 JDBC 同步执行器提供运行记录。
--       每次 trigger 生成一条 run（PENDING），执行线程开始后置 RUNNING，
--       结束后置 SUCCESS/FAILED，并记录读取/写入行数与失败原因。
CREATE SCHEMA IF NOT EXISTS dam_integration;

CREATE TABLE IF NOT EXISTS dam_integration.integration_run (
    id            BIGSERIAL    PRIMARY KEY,
    job_id        BIGINT       NOT NULL,
    run_no        INTEGER      NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    rows_read     BIGINT       NOT NULL DEFAULT 0,
    rows_written  BIGINT       NOT NULL DEFAULT 0,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    error_message TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_integration_run_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT uk_integration_run_job_run_no
        UNIQUE (job_id, run_no)
);

COMMENT ON TABLE dam_integration.integration_run IS '数据集成任务运行记录：一次 trigger 对应一条 run';
COMMENT ON COLUMN dam_integration.integration_run.job_id IS '所属集成任务ID';
COMMENT ON COLUMN dam_integration.integration_run.run_no IS '同一任务内的运行序号，从 1 开始';
COMMENT ON COLUMN dam_integration.integration_run.status IS '运行状态：PENDING/RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN dam_integration.integration_run.rows_read IS '从源端读取的行数';
COMMENT ON COLUMN dam_integration.integration_run.rows_written IS '已提交写入目标端的行数';
COMMENT ON COLUMN dam_integration.integration_run.started_at IS '开始执行时间';
COMMENT ON COLUMN dam_integration.integration_run.finished_at IS '结束时间';
COMMENT ON COLUMN dam_integration.integration_run.error_message IS '失败原因（已脱敏并截断到 2000 字符）';
COMMENT ON COLUMN dam_integration.integration_run.created_at IS '运行记录创建时间';

-- 运行记录按任务与状态检索
CREATE INDEX IF NOT EXISTS idx_integration_run_job_status
    ON dam_integration.integration_run (job_id, status);
CREATE INDEX IF NOT EXISTS idx_integration_run_status
    ON dam_integration.integration_run (status);

-- 同一任务最多存在一条 PENDING/RUNNING 记录，作为“禁止并发执行”的数据库兜底；
-- 业务层在任务行锁内仍会先返回 409，此索引只防止并发竞态。
CREATE UNIQUE INDEX IF NOT EXISTS uk_integration_run_active
    ON dam_integration.integration_run (job_id)
    WHERE status IN ('PENDING', 'RUNNING');
