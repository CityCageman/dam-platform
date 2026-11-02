-- =====================================================================
-- v6 血缘节点 schema 化：回滚（只回滚新增对象，不动旧数据）
-- 注意：如果新代码已经使用 node_key，请先回退代码，再执行本脚本。
-- =====================================================================

DROP VIEW IF EXISTS dam_metadata.v_lineage_node_dup;

DROP INDEX CONCURRENTLY IF EXISTS dam_metadata.idx_lineage_source_node_key;

-- node_key 列默认保留：旧应用不读它，保留无副作用。
-- 如确认要彻底回滚，在维护窗口执行：
-- ALTER TABLE dam_metadata.metadata_lineage_source DROP COLUMN IF EXISTS node_key;
--
-- schema_name/datasource_id/table_id 属于 v4，且新代码在用，回滚 v6 时不要删除。
