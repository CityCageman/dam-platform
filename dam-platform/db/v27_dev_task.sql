-- v27: 数据开发任务定义与版本管理（dam-develop 独立 schema）
-- 说明：仅持久化 SQL/Python/Shell 任务定义、版本快照与发布状态，
--       不包含真实执行引擎、调度记录或运行日志。

CREATE SCHEMA IF NOT EXISTS dam_dev;

-- ---------- 数据开发任务定义 ----------
CREATE TABLE IF NOT EXISTS dam_dev.dev_task (
    id              BIGSERIAL    PRIMARY KEY,
    task_code       VARCHAR(64)  NOT NULL,
    task_name       VARCHAR(128) NOT NULL,
    task_type       VARCHAR(16)  NOT NULL,
    datasource_id   BIGINT,
    content         TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    current_version INT          NOT NULL DEFAULT 1,
    description     TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    deleted_at      TIMESTAMP,
    CONSTRAINT uk_dev_task_task_code UNIQUE (task_code),
    CONSTRAINT ck_dev_task_type CHECK (task_type IN ('SQL', 'PYTHON', 'SHELL')),
    CONSTRAINT ck_dev_task_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE')),
    CONSTRAINT ck_dev_task_current_version CHECK (current_version >= 1)
);

COMMENT ON TABLE dam_dev.dev_task IS '数据开发任务定义（SQL/PYTHON/SHELL，仅管理定义与发布状态，不执行脚本）';
COMMENT ON COLUMN dam_dev.dev_task.task_code IS '任务编码，全局唯一，创建后不可修改';
COMMENT ON COLUMN dam_dev.dev_task.task_name IS '任务名称';
COMMENT ON COLUMN dam_dev.dev_task.task_type IS '任务类型：SQL/PYTHON/SHELL';
COMMENT ON COLUMN dam_dev.dev_task.datasource_id IS '关联数据源 ID，可为空';
COMMENT ON COLUMN dam_dev.dev_task.content IS '当前版本的脚本内容';
COMMENT ON COLUMN dam_dev.dev_task.status IS '发布状态：DRAFT/PUBLISHED/OFFLINE';
COMMENT ON COLUMN dam_dev.dev_task.current_version IS '当前版本号，与 dev_task_version 中最新版本一致';
COMMENT ON COLUMN dam_dev.dev_task.deleted_at IS '逻辑删除时间，NULL 表示未删除';

-- 任务编码唯一索引；type/status 用于列表筛选
CREATE INDEX IF NOT EXISTS idx_dev_task_code
    ON dam_dev.dev_task (task_code);
CREATE INDEX IF NOT EXISTS idx_dev_task_type
    ON dam_dev.dev_task (task_type);
CREATE INDEX IF NOT EXISTS idx_dev_task_status
    ON dam_dev.dev_task (status);

-- ---------- 数据开发任务版本 ----------
CREATE TABLE IF NOT EXISTS dam_dev.dev_task_version (
    id          BIGSERIAL   PRIMARY KEY,
    task_id     BIGINT      NOT NULL,
    version_no  INT         NOT NULL,
    content     TEXT        NOT NULL,
    change_note VARCHAR(500),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(64) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_dev_task_version_task
        FOREIGN KEY (task_id) REFERENCES dam_dev.dev_task (id) ON DELETE CASCADE,
    CONSTRAINT uk_dev_task_version_task_version UNIQUE (task_id, version_no),
    CONSTRAINT ck_dev_task_version_no CHECK (version_no >= 1)
);

COMMENT ON TABLE dam_dev.dev_task_version IS '数据开发任务版本快照：每次 content 变更追加一条记录';
COMMENT ON COLUMN dam_dev.dev_task_version.task_id IS '所属任务 ID';
COMMENT ON COLUMN dam_dev.dev_task_version.version_no IS '版本号，从 1 开始，同一任务内唯一';
COMMENT ON COLUMN dam_dev.dev_task_version.content IS '该版本的脚本内容快照';
COMMENT ON COLUMN dam_dev.dev_task_version.change_note IS '版本变更说明';
COMMENT ON COLUMN dam_dev.dev_task_version.created_by IS '版本创建人';

-- 同一任务内版本号唯一；按任务查询版本列表
CREATE INDEX IF NOT EXISTS idx_dev_task_version_task
    ON dam_dev.dev_task_version (task_id);
