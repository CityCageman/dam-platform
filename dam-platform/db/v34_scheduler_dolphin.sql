-- v34: 调度执行引擎接入 DolphinScheduler（dam-scheduler）
-- 说明：为调度任务增加执行引擎选择与 DS 流程定义绑定，为运行记录增加 DS 流程实例ID
--       与 TaskCenter 实例ID；全部为可空或带默认值的增量列，不改写既有数据，
--       LOCAL 执行语义（本地顺序 SQL DAG）保持不变。DS 默认不启用。
--
-- 执行语义：
--   schedule_job.execution_engine = 'LOCAL'   → ScheduleRunExecutor 执行本地 DAG
--   schedule_job.execution_engine = 'DOLPHIN' → 触发 DS 流程实例，轮询回写终态

ALTER TABLE dam_scheduler.schedule_job
    ADD COLUMN IF NOT EXISTS execution_engine varchar(16) DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS dolphin_process_definition_code bigint,
    ADD COLUMN IF NOT EXISTS dolphin_process_definition_version int;

-- 存量任务保持本地执行器；只回填 NULL，不覆盖已有值
UPDATE dam_scheduler.schedule_job
   SET execution_engine = 'LOCAL'
 WHERE execution_engine IS NULL;

COMMENT ON COLUMN dam_scheduler.schedule_job.execution_engine
    IS '执行引擎：LOCAL=本地顺序 SQL DAG，DOLPHIN=DolphinScheduler';
COMMENT ON COLUMN dam_scheduler.schedule_job.dolphin_process_definition_code
    IS 'DolphinScheduler 流程定义编码，execution_engine=DOLPHIN 时使用';
COMMENT ON COLUMN dam_scheduler.schedule_job.dolphin_process_definition_version
    IS 'DolphinScheduler 流程定义版本，NULL 表示由 DS 侧决定';

-- 引擎筛选（可选）
CREATE INDEX IF NOT EXISTS idx_schedule_job_execution_engine
    ON dam_scheduler.schedule_job (execution_engine);

ALTER TABLE dam_scheduler.schedule_run
    ADD COLUMN IF NOT EXISTS dolphin_process_instance_id bigint,
    ADD COLUMN IF NOT EXISTS task_center_instance_id bigint;

COMMENT ON COLUMN dam_scheduler.schedule_run.dolphin_process_instance_id
    IS 'DolphinScheduler 流程实例ID；非空表示该 run 由 DS 执行并轮询回写终态';
COMMENT ON COLUMN dam_scheduler.schedule_run.task_center_instance_id
    IS 'TaskCenter 运行实例ID，供 DS 终态时回写 finish；LOCAL 运行可为空';

-- 轮询只扫描 RUNNING 且已回填 DS 实例ID的记录；部分索引避免全表扫描
CREATE INDEX IF NOT EXISTS idx_schedule_run_dolphin_poll
    ON dam_scheduler.schedule_run (run_status, dolphin_process_instance_id)
    WHERE dolphin_process_instance_id IS NOT NULL;
