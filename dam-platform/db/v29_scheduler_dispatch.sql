-- v29: 调度任务 Cron 自动派发（dam-scheduler）
-- 说明：为已启用的调度任务记录最近一次自动派发时间，供后续扫描计算下一个触发点；
--       仅新增调度任务元数据列与索引，不改变 schedule_run 表结构，也不引入 DAG 执行器。
ALTER TABLE dam_scheduler.schedule_job
    ADD COLUMN IF NOT EXISTS last_dispatched_at TIMESTAMP;

COMMENT ON COLUMN dam_scheduler.schedule_job.last_dispatched_at
    IS '最近一次自动派发时间，NULL 表示尚未自动派发；手动触发不修改该字段';

-- 自动派发扫描按 status=ENABLED 过滤并依赖 last_dispatched_at 计算触发点
CREATE INDEX IF NOT EXISTS idx_schedule_job_status_last_dispatched_at
    ON dam_scheduler.schedule_job (status, last_dispatched_at);
