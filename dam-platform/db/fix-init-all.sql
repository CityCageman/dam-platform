-- =====================================================================
-- init-all.sql 修复补丁（TBase v5.21 / PostgreSQL 10 内核兼容）
-- 说明：仅修复表结构/元数据，不含测试数据 INSERT；可重复执行（幂等）
-- 执行：psql -h 10.162.3.16 -p 11001 -U dam -d damplatform -f fix-init-all.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 清理：PROD / PROD.CIS_Model 已废弃，不再使用
--    （原 init-all.sql 中模拟 HIS 落地结构的贴源区 schema，现移除）
-- ---------------------------------------------------------------------
DROP SCHEMA IF EXISTS "PROD.CIS_Model" CASCADE;
DROP SCHEMA IF EXISTS "PROD" CASCADE;

-- ---------------------------------------------------------------------
-- 2. 修复：ANALYZE 多表语法
--    init-all.sql 第 3181 行 "ANALYZE t1, t2, t3" 为 PG11+ 特性，
--    TBase（PG10 内核）报 syntax error。此处拆分为逐表 ANALYZE。
-- ---------------------------------------------------------------------
ANALYZE hospital_demo.biz_patient_master;
ANALYZE hospital_demo.biz_encounter;
ANALYZE hospital_demo.biz_diagnosis;
