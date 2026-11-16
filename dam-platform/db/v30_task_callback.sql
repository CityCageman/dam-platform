-- v30: TaskCenter 回调来源字段与幂等键
ALTER TABLE dam_task.task_instance
    ADD COLUMN IF NOT EXISTS source_service varchar(64),
    ADD COLUMN IF NOT EXISTS source_task_code varchar(64),
    ADD COLUMN IF NOT EXISTS source_run_id varchar(128),
    ADD COLUMN IF NOT EXISTS title varchar(200),
    ADD COLUMN IF NOT EXISTS payload text;

CREATE UNIQUE INDEX IF NOT EXISTS uk_task_instance_source_run
    ON dam_task.task_instance (source_service, source_task_code, source_run_id)
    WHERE source_run_id IS NOT NULL;

COMMENT ON COLUMN dam_task.task_instance.source_service IS '回调来源服务，如 dam-integration';
COMMENT ON COLUMN dam_task.task_instance.source_task_code IS '回调来源任务编码';
COMMENT ON COLUMN dam_task.task_instance.source_run_id IS '回调来源运行ID，与来源服务/任务组成幂等键';
COMMENT ON COLUMN dam_task.task_instance.title IS '回调运行标题';
COMMENT ON COLUMN dam_task.task_instance.payload IS '回调运行参数 JSON 文本';
