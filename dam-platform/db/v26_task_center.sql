-- v26: 统一任务中心（任务定义 / 任务实例 / 任务日志）
-- 说明：dam-task-center 只提供任务元数据、运行记录与日志的数据库支撑，
--       不包含任务执行器；触发接口仅写入 PENDING 实例，不伪造执行成功。
CREATE SCHEMA IF NOT EXISTS dam_task;

-- ---------- 任务定义 ----------
CREATE TABLE IF NOT EXISTS dam_task.task_definition (
    id             BIGSERIAL    PRIMARY KEY,
    task_code      VARCHAR(64)  NOT NULL,
    task_name      VARCHAR(200) NOT NULL,
    task_type      VARCHAR(32)  NOT NULL,
    source_service VARCHAR(64)  NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    config         JSONB,
    description    TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    deleted_at     TIMESTAMP,
    CONSTRAINT ck_task_definition_task_type
        CHECK (task_type IN ('INTEGRATION', 'SCHEDULER', 'DEV', 'QUALITY', 'AI')),
    CONSTRAINT ck_task_definition_status
        CHECK (status IN ('DRAFT', 'ENABLED', 'DISABLED'))
);

COMMENT ON TABLE dam_task.task_definition IS '统一任务定义表：跨服务登记任务元数据';
COMMENT ON COLUMN dam_task.task_definition.task_code IS '任务编码，全局唯一，创建后不可修改';
COMMENT ON COLUMN dam_task.task_definition.task_name IS '任务名称';
COMMENT ON COLUMN dam_task.task_definition.task_type IS '任务类型：INTEGRATION/SCHEDULER/DEV/QUALITY/AI';
COMMENT ON COLUMN dam_task.task_definition.source_service IS '来源服务，如 dam-integration、dam-scheduler';
COMMENT ON COLUMN dam_task.task_definition.status IS '状态：DRAFT/ENABLED/DISABLED';
COMMENT ON COLUMN dam_task.task_definition.config IS '任务配置（JSONB）';
COMMENT ON COLUMN dam_task.task_definition.deleted_at IS '逻辑删除时间，NULL 表示未删除';

-- task_code 唯一索引同时承担编码检索；task_type/status 用于列表筛选
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_definition_task_code
    ON dam_task.task_definition (task_code);
CREATE INDEX IF NOT EXISTS idx_task_definition_task_type
    ON dam_task.task_definition (task_type);
CREATE INDEX IF NOT EXISTS idx_task_definition_status
    ON dam_task.task_definition (status);

-- ---------- 任务实例 ----------
CREATE TABLE IF NOT EXISTS dam_task.task_instance (
    id            BIGSERIAL    PRIMARY KEY,
    definition_id BIGINT       NOT NULL,
    task_code     VARCHAR(64)  NOT NULL,
    run_no        INTEGER      NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    trigger_type  VARCHAR(16)  NOT NULL,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    error_message TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_task_instance_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_task_instance_trigger_type
        CHECK (trigger_type IN ('MANUAL', 'SCHEDULE', 'API')),
    CONSTRAINT uk_task_instance_definition_run
        UNIQUE (definition_id, run_no)
);

COMMENT ON TABLE dam_task.task_instance IS '任务实例表：一次触发对应一条实例记录';
COMMENT ON COLUMN dam_task.task_instance.definition_id IS '所属任务定义ID';
COMMENT ON COLUMN dam_task.task_instance.task_code IS '触发时的任务编码快照';
COMMENT ON COLUMN dam_task.task_instance.run_no IS '同一任务定义内的运行序号，从 1 开始';
COMMENT ON COLUMN dam_task.task_instance.status IS '状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED';
COMMENT ON COLUMN dam_task.task_instance.trigger_type IS '触发方式：MANUAL/SCHEDULE/API';
COMMENT ON COLUMN dam_task.task_instance.started_at IS '开始执行时间，由执行器或状态回填接口维护';
COMMENT ON COLUMN dam_task.task_instance.finished_at IS '结束时间';
COMMENT ON COLUMN dam_task.task_instance.error_message IS '失败原因';

CREATE INDEX IF NOT EXISTS idx_task_instance_definition_id
    ON dam_task.task_instance (definition_id);
CREATE INDEX IF NOT EXISTS idx_task_instance_status
    ON dam_task.task_instance (status);
CREATE INDEX IF NOT EXISTS idx_task_instance_created_at
    ON dam_task.task_instance (created_at);

-- ---------- 任务日志 ----------
CREATE TABLE IF NOT EXISTS dam_task.task_log (
    id          BIGSERIAL   PRIMARY KEY,
    instance_id BIGINT      NOT NULL,
    log_level   VARCHAR(16) NOT NULL DEFAULT 'INFO',
    message     TEXT        NOT NULL,
    logged_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_task_log_level
        CHECK (log_level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'))
);

COMMENT ON TABLE dam_task.task_log IS '任务日志表：按实例追加的执行日志';
COMMENT ON COLUMN dam_task.task_log.instance_id IS '所属任务实例ID';
COMMENT ON COLUMN dam_task.task_log.log_level IS '日志级别：TRACE/DEBUG/INFO/WARN/ERROR';
COMMENT ON COLUMN dam_task.task_log.message IS '日志内容';
COMMENT ON COLUMN dam_task.task_log.logged_at IS '日志记录时间';

CREATE INDEX IF NOT EXISTS idx_task_log_instance_id
    ON dam_task.task_log (instance_id);
