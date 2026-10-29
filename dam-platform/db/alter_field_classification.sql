-- ============================================================
-- 字段分类分级：给现有 metadata_column 表增加安全等级与敏感类型列
-- 适用：已按旧版 create_schema_doc.sql 建库的存量环境
-- 执行：psql -U dam -d damplatform -f alter_field_classification.sql
-- 说明：全新环境无需执行（create_schema_doc.sql 已包含这两列）
-- ============================================================

ALTER TABLE dam_metadata.metadata_column ADD COLUMN IF NOT EXISTS security_level VARCHAR(20);
ALTER TABLE dam_metadata.metadata_column ADD COLUMN IF NOT EXISTS sensitive_type VARCHAR(50);
ALTER TABLE dam_metadata.metadata_column ADD COLUMN IF NOT EXISTS reference_table_name VARCHAR(200);

COMMENT ON COLUMN dam_metadata.metadata_column.security_level IS '安全等级：public-公开，internal-内部，confidential-内部敏感，restricted-重要数据，critical-核心数据';
COMMENT ON COLUMN dam_metadata.metadata_column.sensitive_type IS '敏感类型（字典 sensitive_type，如身份证号/手机号/姓名）';
COMMENT ON COLUMN dam_metadata.metadata_column.reference_table_name IS '外键/指针引用表名（同步字段时从 JDBC getImportedKeys 获取，供血缘识别）';

-- 敏感类型字典（存量环境需手动补入，字典项可在「系统配置-字典管理」中自行维护）
INSERT INTO dam_system.sys_dict_type (dict_type_code, dict_type_name, remark, status, created_by, updated_by)
SELECT 'sensitive_type', '敏感类型', 'metadata_column.sensitive_type 引用', 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_system.sys_dict_type WHERE dict_type_code = 'sensitive_type');

INSERT INTO dam_system.sys_dict_data (dict_type_id, dict_label, dict_value, sort_order, is_default, status, created_by, updated_by)
SELECT d.id, v.dict_label, v.dict_value, v.sort_order, FALSE, 1, 'system', 'system'
FROM dam_system.sys_dict_type d
JOIN (VALUES ('身份证号','id_card',1), ('手机号','mobile',2), ('姓名','name',3),
              ('家庭住址','address',4), ('病历号','medical_record_no',5), ('诊断','diagnosis',6),
              ('费用金额','fee_amount',7), ('社保卡号','social_security_no',8))
AS v(dict_label, dict_value, sort_order) ON d.dict_type_code = 'sensitive_type'
WHERE NOT EXISTS (
    SELECT 1 FROM dam_system.sys_dict_data dd
    WHERE dd.dict_type_id = d.id AND dd.dict_value = v.dict_value
);
