-- v31: 数据开发 SQL 任务执行数据源配置
ALTER TABLE dam_dev.dev_task
    ADD COLUMN IF NOT EXISTS datasource_config JSONB;

COMMENT ON COLUMN dam_dev.dev_task.datasource_config IS
    'SQL 任务 JDBC 数据源配置（url/username/password/queryTimeoutSeconds/maxRows），含密码，禁止日志输出';
