-- v32: 调度 DAG 定义（dam-scheduler）
-- 说明：仅新增基础顺序 SQL DAG 定义表，支持节点顺序执行；不包含并行分支、
--       条件分支与重试编排。datasource.password 以 jsonb 原文保存，读取详情时脱敏。

CREATE TABLE IF NOT EXISTS dam_scheduler.schedule_dag (
    dag_code   varchar(64) primary key,
    dag_name   varchar(200) not null,
    definition jsonb not null,
    description text,
    status     varchar(16) not null default 'ENABLED',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint ck_schedule_dag_status check (status in ('ENABLED','DISABLED'))
);
