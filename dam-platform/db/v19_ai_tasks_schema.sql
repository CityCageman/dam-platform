-- v19: AI 任务中心表归属 dam_ai schema，避免 catalog 与 ai-workbench 共享 dam_asset 业务 schema
CREATE SCHEMA IF NOT EXISTS dam_ai;

ALTER TABLE IF EXISTS dam_asset.ai_task SET SCHEMA dam_ai;
ALTER TABLE IF EXISTS dam_asset.ai_task_item SET SCHEMA dam_ai;

-- serial/identity 关联序列不会总是随表移动，显式迁移 ai_task 相关序列
DO $$
DECLARE seq record;
BEGIN
    FOR seq IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'dam_asset'
          AND c.relkind = 'S'
          AND (c.relname LIKE 'ai_task%' OR c.relname LIKE 'ai_task_item%')
    LOOP
        EXECUTE format('ALTER SEQUENCE dam_asset.%I SET SCHEMA dam_ai', seq.relname);
    END LOOP;
END $$;

-- 索引保留在表所在 schema；后续可按服务账号授权
