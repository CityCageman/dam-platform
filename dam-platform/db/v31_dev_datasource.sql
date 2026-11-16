-- v31: 数据开发 SQL 任务执行用数据源配置（dam_dev.dev_task.datasource_config）
-- 说明：
--   1. 该列仅 SQL 任务使用：保存执行只读 SELECT/WITH 所需的 PostgreSQL JDBC 配置；
--      Python/Shell 任务不使用该字段（保持 NULL），本服务也不实现其执行能力。
--   2. JSON 结构示例（字段均可选，执行时校验默认值/上限）：
--      {"url":"jdbc:postgresql://127.0.0.1:5432/db",
--       "username":"readonly",
--       "password":"***",
--       "queryTimeoutSeconds":30,
--       "maxRows":1000}
--      url 仅允许 jdbc:postgresql:// 前缀；queryTimeoutSeconds 默认 30、最大 300；
--      maxRows 默认 1000、最大 5000。
--   3. 该列包含密码，服务端日志与执行结果均不得输出其内容；
--      建议执行账号仅授予只读权限，并限制能修改 datasource_config 的人员范围。
ALTER TABLE dam_dev.dev_task
    ADD COLUMN IF NOT EXISTS datasource_config jsonb;

COMMENT ON COLUMN dam_dev.dev_task.datasource_config IS
    '仅 SQL 任务使用的 JDBC 配置（JSONB）：url/username/password/queryTimeoutSeconds/maxRows；Python/Shell 不使用；含密码，禁止写入日志';
