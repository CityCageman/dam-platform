-- v35: 将历史 sys_config 中的枚举型配置迁移为通用字典
-- 背景：安全等级、资产类型、资产状态、数据源类型、敏感级别、技术元数据类型
--       本质是字典数据，不应继续作为独立配置菜单维护。
-- 原则：只新增 sys_dict_type/sys_dict_data，不删除 sys_config，保留历史可追溯；
--       所有语句幂等，可重复执行。

-- 1. 字典类型
INSERT INTO dam_sys.sys_dict_type
    (dict_type_code, dict_type_name, remark, status, created_by, updated_by)
SELECT v.code, v.name, v.remark, 1, 'system', 'system'
FROM (VALUES
    ('security_level',  '安全等级',     '数据安全分级字典，迁移自 sys_config.security_level'),
    ('asset_type',      '资产类型',     '数据资产类型字典，迁移自 sys_config.asset_type'),
    ('asset_status',    '资产状态',     '数据资产状态字典，迁移自 sys_config.asset_status'),
    ('sensitive_level', '敏感级别',     '敏感数据级别字典，迁移自 sys_config.sensitive_level'),
    ('tech_asset_type', '技术元数据类型', '技术元数据类型字典，迁移自 sys_config.tech_asset_type'),
    ('source_type',     '数据源类型',   '数据源连接类型字典，迁移自 sys_config.source_type')
) AS v(code, name, remark)
WHERE NOT EXISTS (
    SELECT 1 FROM dam_sys.sys_dict_type t
    WHERE t.dict_type_code = v.code AND t.deleted_at IS NULL
);

-- 2. 字典数据：config_code -> dict_value，config_name -> dict_label
INSERT INTO dam_sys.sys_dict_data
    (dict_type_id, dict_label, dict_value, dict_extra, sort_order, is_default,
     remarks, status, created_by, updated_by)
SELECT t.id,
       c.config_name,
       c.config_code,
       left(c.config_value, 500),
       COALESCE(c.sort_order, 0),
       FALSE,
       c.description,
       COALESCE(c.status, 1),
       'system',
       'system'
FROM dam_sys.sys_config c
JOIN dam_sys.sys_dict_type t
  ON t.dict_type_code = c.config_type
 AND t.deleted_at IS NULL
WHERE c.deleted_at IS NULL
  AND c.config_type IN (
      'security_level', 'asset_type', 'asset_status',
      'sensitive_level', 'tech_asset_type', 'source_type'
  )
  AND NOT EXISTS (
      SELECT 1 FROM dam_sys.sys_dict_data d
      WHERE d.dict_type_id = t.id
        AND d.dict_value = c.config_code
        AND d.deleted_at IS NULL
  );

-- 3. 校验：迁移后各字典类型的数据量
-- SELECT t.dict_type_code, t.dict_type_name, count(d.id)
-- FROM dam_sys.sys_dict_type t
-- LEFT JOIN dam_sys.sys_dict_data d ON d.dict_type_id = t.id AND d.deleted_at IS NULL
-- WHERE t.dict_type_code IN ('security_level','asset_type','asset_status','sensitive_level','tech_asset_type','source_type')
-- GROUP BY t.dict_type_code, t.dict_type_name ORDER BY t.dict_type_code;
