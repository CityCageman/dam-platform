-- ============================================================
-- 数据资产管理平台 - 初始化数据脚本（幂等版，schema = dam_sys）
-- 执行：psql -d damplatform -f 初始化.sql
-- 特性：全部 INSERT 幂等（NOT EXISTS），可重复执行；sys_menu 缺失自动建表
-- 覆盖：角色 / 用户 / 菜单 / 配置中心 / 字典 / 来源系统 / 元数据分类 / 资产分类
-- ============================================================

-- ==================== 0. 建表兜底 ====================
CREATE TABLE IF NOT EXISTS dam_sys.sys_menu (
  id BIGSERIAL PRIMARY KEY,
  menu_name VARCHAR(100) NOT NULL,
  menu_type VARCHAR(20) NOT NULL DEFAULT 'C',
  parent_id BIGINT,
  tree_level INTEGER DEFAULT 1,
  path VARCHAR(200),
  component VARCHAR(200),
  redirect VARCHAR(200),
  icon VARCHAR(100),
  sort_order INTEGER DEFAULT 0,
  visible SMALLINT NOT NULL DEFAULT 1,
  status SMALLINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(100) NOT NULL DEFAULT '系统',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(100) NOT NULL DEFAULT '系统',
  deleted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sys_menu_parent ON dam_sys.sys_menu(parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_menu_path ON dam_sys.sys_menu(path);

-- ==================== 1. 角色 ====================
INSERT INTO dam_sys.sys_role (role_code, role_name, description, status, created_by, updated_by)
SELECT v.role_code, v.role_name, v.description, 1, 'system', 'system'
FROM (VALUES
  ('governor','数据管理员','元数据维护、标准制定、质量规则配置'),
  ('developer','数据开发者','资产注册、血缘梳理、工单处理'),
  ('business','业务用户','资产浏览、检索申请、数据使用'),
  ('security','安全管理员','分级管控、审计日志、合规检查'),
  ('management','管理层','运营看板、资产价值评估、治理成效报告'),
  ('steward','数据管家','数据质量日常监控、标准执行与资产治理'),
  ('analyst','数据分析师','资产检索、数据查看与分析使用')
) AS v(role_code, role_name, description)
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_role r WHERE r.role_code = v.role_code AND r.deleted_at IS NULL);

-- ==================== 2. 内置用户（BCrypt；password_hash/salt 为遗留 NOT NULL 占位列） ====================
INSERT INTO dam_sys.sys_user (username, password, password_hash, salt, real_name, email, phone, department, last_login_at, last_login_ip, status, created_by, updated_by)
SELECT v.username, v.password, v.password, '', v.real_name, v.email, v.phone, v.department, CURRENT_TIMESTAMP, '127.0.0.1', 1, 'system', 'system'
FROM (VALUES
  ('admin','$2a$10$8DBgEYtarH9NsXmQ7olXbuYoOhszYQmeyPlmskJXxtJb/TZ77fLYG','系统管理员','admin@dam.local','13800000000','信息中心'),
  ('steward','$2a$10$9Kv27Z/WJOmDuUHdoItke.b6izEkMeNA/iNkPC47qae5nZJmvaYK.','数据管家','steward@dam.local','13800000001','数据治理办公室'),
  ('analyst','$2a$10$lAB1aGc6NM.CNYY4mb0koO.kM9.X9hG7gbsPOpxnQgY3//YjiXMz.','数据分析师','analyst@dam.local','13800000002','数据分析中心')
) AS v(username, password, real_name, email, phone, department)
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_user u WHERE u.username = v.username AND u.deleted_at IS NULL);

-- ==================== 3. 用户-角色关联 ====================
INSERT INTO dam_sys.sys_user_role (user_id, role_id, default_role, status, created_by, updated_by)
SELECT u.id, r.id, 'Y', 1, 'system', 'system'
FROM dam_sys.sys_user u, dam_sys.sys_role r
WHERE ((u.username='admin' AND r.role_code='governor') OR (u.username='steward' AND r.role_code='steward') OR (u.username='analyst' AND r.role_code='analyst'))
  AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_user_role x WHERE x.user_id=u.id AND x.role_id=r.id AND x.deleted_at IS NULL);

-- ==================== 4. 系统菜单（4 目录 + 11 页面） ====================
INSERT INTO dam_sys.sys_menu (menu_name, menu_type, parent_id, tree_level, icon, sort_order, visible, status, created_by, updated_by)
SELECT v.menu_name, 'M', NULL, 1, v.icon, v.sort_order, 1, 1, 'system', 'system'
FROM (VALUES ('总览','DataBoard',1),('数据资产','Folder',2),('治理能力','Setting',3),('平台运营','Connection',4)) AS v(menu_name, icon, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_menu m WHERE m.menu_name=v.menu_name AND m.parent_id IS NULL AND m.deleted_at IS NULL);

INSERT INTO dam_sys.sys_menu (menu_name, menu_type, parent_id, tree_level, path, icon, sort_order, visible, status, created_by, updated_by)
SELECT v.menu_name, 'C', (SELECT p.id FROM dam_sys.sys_menu p WHERE p.menu_name=v.parent_name AND p.parent_id IS NULL AND p.deleted_at IS NULL LIMIT 1), 2, v.path, v.icon, v.sort_order, 1, 1, 'system', 'system'
FROM (VALUES
  ('运营看板','总览','/dashboard','DataAnalysis',1),
  ('资产目录','数据资产','/catalog','Folder',1),
  ('元数据管理','数据资产','/metadata','DataBoard',2),
  ('模型管理','数据资产','/models','Collection',3),
  ('数据血缘','数据资产','/lineage','Connection',4),
  ('数据标准','治理能力','/standard','Document',1),
  ('数据质量','治理能力','/quality','List',2),
  ('安全管控','治理能力','/security','Lock',3),
  ('生命周期','治理能力','/lifecycle','Timer',4),
  ('工单中心','平台运营','/operations','Tickets',1),
  ('智能助手','平台运营','/agent','ChatDotRound',2)
) AS v(menu_name, parent_name, path, icon, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_menu m WHERE m.path=v.path AND m.deleted_at IS NULL);

-- ==================== 5. 角色-菜单关联 ====================
INSERT INTO dam_sys.sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 1, 'system', 'system'
FROM dam_sys.sys_role r, dam_sys.sys_menu m
WHERE r.role_code IN ('governor','steward','analyst') AND m.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.deleted_at IS NULL);

-- ==================== 6. 系统配置（基础 + 配置中心 7 类） ====================
INSERT INTO dam_sys.sys_config (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by)
SELECT v.config_type, v.config_code, v.config_name, v.config_value, v.description, v.sort_order, 1, 'system', 'system'
FROM (VALUES
  ('SYSTEM','sys_name','系统名称','数据资产管理平台','平台名称',1),
  ('SYSTEM','sys_version','系统版本','v1.0.0','版本号',2),
  ('SECURITY','pwd_min_len','密码最小长度','6','系统密码策略',1),
  ('JOB','scan_timeout','扫描超时(秒)','300','数据源扫描超时',1),
  ('navigation','nav_dashboard','运营看板','/dashboard','运营看板导航',1),
  ('navigation','nav_catalog','资产目录','/catalog','资产目录导航',2),
  ('navigation','nav_metadata','元数据管理','/metadata','元数据管理导航',3),
  ('navigation','nav_model','模型管理','/models','模型管理导航',4),
  ('navigation','nav_lineage','数据血缘','/lineage','数据血缘导航',5),
  ('navigation','nav_standard','数据标准','/standard','数据标准导航',6),
  ('navigation','nav_quality','数据质量','/quality','数据质量导航',7),
  ('navigation','nav_security','安全管控','/security','安全管控导航',8),
  ('navigation','nav_lifecycle','生命周期','/lifecycle','生命周期导航',9),
  ('navigation','nav_operations','工单中心','/operations','工单中心导航',10),
  ('navigation','nav_agent','智能助手','/agent','智能助手导航',11),
  ('security_level','LEVEL_1','1级：公开数据','public','对外公开数据',1),
  ('security_level','LEVEL_2','2级：内部一般数据','internal','内部一般数据',2),
  ('security_level','LEVEL_3','3级：内部敏感数据','confidential','内部敏感数据',3),
  ('security_level','LEVEL_4','4级：重要数据/个人信息','restricted','重要数据/个人信息',4),
  ('security_level','LEVEL_5','5级：核心数据','critical','核心数据',5),
  ('asset_type','table','数据表','table','关系型数据表',1),
  ('asset_type','view','视图','view','数据库视图',2),
  ('asset_type','file','文件','file','文件类资产',3),
  ('asset_type','api','API','api','接口类资产',4),
  ('asset_status','published','已发布','1','已发布状态',1),
  ('asset_status','draft','草稿','2','草稿状态',2),
  ('asset_status','offline','已下线','3','已下线状态',3),
  ('sensitive_level','LOW','低','low','低敏感',1),
  ('sensitive_level','MEDIUM','中','medium','中敏感',2),
  ('sensitive_level','HIGH','高','high','高敏感',3),
  ('tech_asset_type','TABLE','数据表','TABLE','技术元数据-表',1),
  ('tech_asset_type','VIEW','视图','VIEW','技术元数据-视图',2),
  ('tech_asset_type','FILE','文件','FILE','技术元数据-文件',3),
  ('tech_asset_type','API','API','API','技术元数据-接口',4),
  ('tech_asset_type','REPORT','报表','REPORT','技术元数据-报表',5),
  ('source_type','POSTGRESQL','PostgreSQL','{"driverClass":"org.postgresql.Driver","urlTemplate":"jdbc:postgresql://%s:%d/%s","defaultPort":5432,"catalogMode":"database","pkStrategy":"pg_catalog","systemSchemas":"information_schema,pg_catalog"}','PostgreSQL 数据库',1),
  ('source_type','MYSQL','MySQL','{"driverClass":"com.mysql.cj.jdbc.Driver","urlTemplate":"jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai","defaultPort":3306,"catalogMode":"database","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"information_schema,mysql,performance_schema,sys"}','MySQL 数据库',2),
  ('source_type','ORACLE','Oracle','{"driverClass":"oracle.jdbc.OracleDriver","urlTemplate":"jdbc:oracle:thin:@//%s:%d/%s","defaultPort":1521,"catalogMode":"schema","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"SYS,SYSTEM"}','Oracle 数据库',3),
  ('source_type','SQLSERVER','SQL Server','{"driverClass":"com.microsoft.sqlserver.jdbc.SQLServerDriver","urlTemplate":"jdbc:sqlserver://%s:%d;databaseName=%s","defaultPort":1433,"catalogMode":"database","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"sys"}','SQL Server 数据库',4),
  ('source_type','IRIS','IRIS','{"driverClass":"com.intersystems.jdbc.IRISDriver","urlTemplate":"jdbc:IRIS://%s:%d/%s","defaultPort":1972,"catalogMode":"schema","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"IRISSYS"}','InterSystems IRIS 数据库',5),
  ('source_type','DM8','达梦','{"driverClass":"dm.jdbc.driver.DmDriver","urlTemplate":"jdbc:dm://%s:%d/%s","defaultPort":5236,"catalogMode":"schema","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"SYSAUDITOR,SYSDBA"}','达梦数据库',6),
  ('source_type','KINGBASE','人大金仓','{"driverClass":"com.kingbase8.Driver","urlTemplate":"jdbc:kingbase8://%s:%d/%s","defaultPort":54321,"catalogMode":"database","pkStrategy":"pg_catalog","systemSchemas":"information_schema,pg_catalog"}','人大金仓数据库',7)
) AS v(config_type, config_code, config_name, config_value, description, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_config c WHERE c.config_type=v.config_type AND c.config_code=v.config_code);

-- ==================== 7. 来源系统 / 元数据分类 / 资产分类 ====================
INSERT INTO dam_metadata.source_system (system_code, system_name, description, status, created_by, updated_by)
SELECT v.code, v.name, v.description, 1, 'system', 'system'
FROM (VALUES
  ('HIS','医院信息系统','Hospital Information System'),('LIS','检验信息系统','Laboratory Information System'),
  ('RIS','放射信息系统','Radiology Information System'),('PACS','影像归档系统','Picture Archiving and Communication System'),
  ('EMR','电子病历系统','Electronic Medical Record'),('CIS','临床信息系统','Clinical Information System'),
  ('NIS','护理信息系统','Nursing Information System'),('HRP','医院资源规划','Hospital Resource Planning')
) AS v(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM dam_metadata.source_system s WHERE s.system_code = v.code);

INSERT INTO dam_metadata.metadata_category (category_name, category_code, category_type, parent_id, tree_level, full_path, description, sort_order, status, created_by, updated_by)
SELECT v.name, v.code, 'ROOT', NULL, 1, '/' || v.name, v.description, v.sort_order, 1, 'system', 'system'
FROM (VALUES
  ('技术元数据','TECHNICAL','描述数据技术特征（库、表、字段等）',1),
  ('业务元数据','BUSINESS','描述业务含义与规则',2),
  ('管理元数据','MANAGEMENT','描述治理、权限、生命周期等管理信息',3)
) AS v(name, code, description, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM dam_metadata.metadata_category c WHERE c.category_code = v.code);

-- 资产分类（dam_asset.asset_category）
INSERT INTO dam_asset.asset_category (category_code, category_name, parent_id, tree_level, category_path, description, sort_order, status, created_by, updated_by)
SELECT v.code, v.name, NULL, 1, '/' || v.name, v.description, v.sort_order, 1, 'system', 'system'
FROM (VALUES ('HOSPITAL','医疗数据','医疗业务数据资产',1),('CLINICAL','临床数据','临床诊断治疗相关',2),('MANAGE','管理数据','医院运营管理相关',3)) AS v(code, name, description, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM dam_asset.asset_category c WHERE c.category_code = v.code);

-- ==================== 8. 字典类型（sys_dict_type） ====================
INSERT INTO dam_sys.sys_dict_type (dict_type_code, dict_type_name, remark, status, created_by, updated_by)
SELECT v.code, v.name, v.remark, 1, 'system', 'system'
FROM (VALUES
  ('asset_type','资产类型','asset_info.asset_type_id'),
  ('mapping_type','映射类型','metadata_business_table_rel.mapping_type_id'),
  ('governance_status','治理状态','metadata_management.governance_status_id'),
  ('lifecycle_status','生命周期状态','metadata_management.lifecycle_status_id'),
  ('security_classification','安全分级','metadata_management.security_classification'),
  ('data_verification_status','数据验证状态','metadata_management.data_verification_status'),
  ('criticality_level','业务重要性','metadata_business.criticality_level'),
  ('dimension_type','维度类型','model_dimension.dimension_type_id'),
  ('sensitivity_level','敏感等级','security_sensitive_rule.sensitivity_level'),
  ('rule_type','敏感规则类型','security_sensitive_rule.rule_type'),
  ('priority','优先级','ticket_order.priority'),
  ('environment','环境标识','metadata_schema.environment'),
  ('schema_type','Schema类型','metadata_schema.schema_type'),
  ('table_type','表类型','metadata_table.table_type'),
  ('ds_type','数据源类型','metadata_datasource.ds_type'),
  ('action_type','操作类型','asset_usage_log.action_type'),
  ('access_type','访问类型','security_access_application.access_type'),
  ('ticket_type','工单类型','ticket_order.ticket_type'),
  ('ticket_status','工单状态','ticket_order.status'),
  ('ticket_priority','工单优先级','ticket_order.priority'),
  ('published_status','资产发布状态','asset_info.published_status'),
  ('audit_operation_type','数据操作审计类型','security_data_audit_log.operation_type'),
  ('operation_type','系统操作类型','sys_audit_log.operation_type'),
  ('check_result_status','质量检测结果状态','metadata_quality_check_result.result_status'),
  ('discovered_by','血缘发现方式','metadata_lineage.discovered_by'),
  ('permission_action','权限操作类型','security_data_permission.action_type'),
  ('resource_type','权限资源类型','security_data_permission.resource_type'),
  ('subject_type','权限主体类型','security_data_permission.subject_type'),
  ('risk_level','风险等级','security_data_audit_log.risk_level'),
  ('masking_type','脱敏类型','security_masking_policy.masking_type'),
  ('relation_type','血缘关系类型','metadata_lineage.relation_type'),
  ('dict_dim_type','基础字典主题域','metadata_table.subject_domain(DIM_*)'),
  ('dict_biz_type','业务主题域','metadata_table.subject_domain'),
  ('sensitive_type','敏感类型','metadata_column.sensitive_type'),
  ('lineage_node_type','血缘节点类型','metadata_lineage_source.source_type_id'),
  ('lineage_mapping_type','字段映射类型','metadata_column_lineage.mapping_type_id'),
  ('lineage_calc_type','字段计算类型','metadata_column_calc_dependency.calc_type_id'),
  ('lineage_parse_status','血缘解析状态','metadata_report_sql_parser.parse_status_id'),
  ('lineage_transform_type','血缘转换类型','metadata_lineage_hierarchy.transform_type_id'),
  ('lineage_aggregation_type','血缘聚合类型','metadata_column_lineage.aggregation_type_id'),
  ('lineage_operator','计算操作符','metadata_column_calc_dependency.dependency_operator_id'),
  ('lineage_layer','血缘分层','metadata_lineage_hierarchy.layer_level'),
  ('report_type','血缘对象类型','metadata_report_sql_parser.report_type_id')
) AS v(code, name, remark)
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_dict_type t WHERE t.dict_type_code = v.code);

-- ==================== 9. 字典数据（sys_dict_data，按 dict_value 幂等） ====================
INSERT INTO dam_sys.sys_dict_data (dict_type_id, dict_label, dict_value, sort_order, is_default, status, created_by, updated_by)
SELECT d.id, v.dict_label, v.dict_value, v.sort_order, FALSE, 1, 'system', 'system'
FROM (VALUES
  ('asset_type','数据表','table',1),('asset_type','视图','view',2),('asset_type','文件','file',3),('asset_type','API','api',4),
  ('mapping_type','技术映射','technical',1),('mapping_type','业务映射','business',2),
  ('governance_status','待治理','TO_BE_GOVERNED',1),('governance_status','治理中','IN_PROGRESS',2),('governance_status','已治理','GOVERNED',3),('governance_status','已忽略','IGNORED',4),
  ('lifecycle_status','活跃','ACTIVE',1),('lifecycle_status','冷数据','COLD',2),('lifecycle_status','已归档','ARCHIVED',3),('lifecycle_status','已清理','PURGED',4),
  ('security_classification','1级：公开数据','public',1),('security_classification','2级：内部一般数据','internal',2),('security_classification','3级：内部敏感数据','confidential',3),('security_classification','4级：重要数据/个人信息','restricted',4),('security_classification','5级：核心数据','critical',5),
  ('data_verification_status','未验证','unverified',1),('data_verification_status','已验证','verified',2),('data_verification_status','验证失败','failed',3),
  ('criticality_level','高','high',1),('criticality_level','中','medium',2),('criticality_level','低','low',3),
  ('dimension_type','维度','dimension',1),('dimension_type','层级','hierarchy',2),('dimension_type','慢变维度','slowly_changing',3),
  ('sensitivity_level','低','low',1),('sensitivity_level','中','medium',2),('sensitivity_level','高','high',3),
  ('sensitive_type','身份证号','id_card',1),('sensitive_type','手机号','mobile',2),('sensitive_type','姓名','name',3),('sensitive_type','家庭住址','address',4),('sensitive_type','病历号','medical_record_no',5),('sensitive_type','诊断','diagnosis',6),('sensitive_type','费用金额','fee_amount',7),('sensitive_type','社保卡号','social_security_no',8),
  ('rule_type','正则表达式','regex',1),('rule_type','词典匹配','dictionary',2),('rule_type','字段名匹配','field_name',3),
  ('priority','高','high',1),('priority','中','medium',2),('priority','低','low',3),
  ('environment','生产环境','PROD',1),('environment','测试环境','TEST',2),('environment','开发环境','DEV',3),
  ('schema_type','数据库','DATABASE',1),('schema_type','目录','CATALOG',2),('schema_type','虚拟','VIRTUAL',3),
  ('table_type','表','TABLE',1),('table_type','视图','VIEW',2),
  ('ds_type','MySQL','MYSQL',1),('ds_type','PostgreSQL','POSTGRESQL',2),('ds_type','Oracle','ORACLE',3),('ds_type','SQL Server','SQLSERVER',4),('ds_type','IRIS','IRIS',5),('ds_type','达梦','DM8',6),('ds_type','人大金仓','KINGBASE',7),
  ('action_type','浏览','view',1),('action_type','查询','query',2),('action_type','下载','download',3),('action_type','导出','export',4),
  ('access_type','查看','view',1),('access_type','查询','query',2),('access_type','导出','export',3),('access_type','下载','download',4),
  ('ticket_type','数据访问申请','data_access',1),('ticket_type','质量问题','quality_issue',2),('ticket_type','变更申请','change_request',3),
  ('ticket_status','待处理','0',1),('ticket_status','处理中','1',2),('ticket_status','已完成','2',3),('ticket_status','已驳回','3',4)
) AS v(type_code, dict_label, dict_value, sort_order)
JOIN dam_sys.sys_dict_type d ON d.dict_type_code = v.type_code
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_dict_data x WHERE x.dict_type_id = d.id AND x.dict_value = v.dict_value);

INSERT INTO dam_sys.sys_dict_data (dict_type_id, dict_label, dict_value, sort_order, is_default, status, created_by, updated_by)
SELECT d.id, v.dict_label, v.dict_value, v.sort_order, FALSE, 1, 'system', 'system'
FROM (VALUES
  ('ticket_priority','低','LOW',1),('ticket_priority','中','MEDIUM',2),('ticket_priority','高','HIGH',3),('ticket_priority','紧急','URGENT',4),
  ('published_status','已发布','1',1),('published_status','草稿','2',2),('published_status','已下线','3',3),
  ('audit_operation_type','查询','QUERY',1),('audit_operation_type','导出','EXPORT',2),('audit_operation_type','下载','DOWNLOAD',3),('audit_operation_type','复制','COPY',4),('audit_operation_type','分析','ANALYZE',5),
  ('operation_type','登录','LOGIN',1),('operation_type','登出','LOGOUT',2),('operation_type','新增','CREATE',3),('operation_type','修改','UPDATE',4),('operation_type','删除','DELETE',5),('operation_type','授权','GRANT',6),('operation_type','访问','ACCESS',7),
  ('check_result_status','通过','PASS',1),('check_result_status','警告','WARN',2),('check_result_status','失败','FAIL',3),
  ('discovered_by','自动解析','PARSER',1),('discovered_by','手动','MANUAL',2),('discovered_by','导入','IMPORT',3),
  ('permission_action','查询','SELECT',1),('permission_action','插入','INSERT',2),('permission_action','更新','UPDATE',3),('permission_action','删除','DELETE',4),('permission_action','导出','EXPORT',5),
  ('resource_type','表','TABLE',1),('resource_type','字段','COLUMN',2),('resource_type','数据库','DATABASE',3),('resource_type','模式','SCHEMA',4),
  ('subject_type','用户','USER',1),('subject_type','角色','ROLE',2),('subject_type','用户组','GROUP',3),('subject_type','部门','DEPT',4),
  ('risk_level','低','LOW',1),('risk_level','中','MEDIUM',2),('risk_level','高','HIGH',3),('risk_level','严重','CRITICAL',4),
  ('masking_type','掩码','MASK',1),('masking_type','替换','REPLACE',2),('masking_type','加密','ENCRYPT',3),('masking_type','哈希','HASH',4),('masking_type','置空','NULL',5),
  ('relation_type','数据流向','FLOW',1),('relation_type','复制','COPY',2),('relation_type','转换','TRANSFORM',3),('relation_type','依赖','DEPEND',4),('relation_type','引用','REFERENCE',5),('relation_type','生成','GENERATE',6),
  ('dict_biz_type','患者域','PATIENT',1),('dict_biz_type','临床域','CLINICAL',2),('dict_biz_type','诊疗域','TREATMENT',3),('dict_biz_type','手术域','SURGERY',4),('dict_biz_type','药品域','MEDICATION',5),('dict_biz_type','费用域','FINANCE',6),('dict_biz_type','管理域','ADMIN',7),('dict_biz_type','基础数据域','REFERENCE',8),('dict_biz_type','日志域','LOG',9),('dict_biz_type','流程域','WORKFLOW',10),
  ('dict_dim_type','药品与药事管理域','DIM_Pharmacy',1),('dict_dim_type','就诊与挂号域','DIM_Encounter',2),('dict_dim_type','医嘱与执行域','DIM_Order',3),('dict_dim_type','诊断与手术域','DIM_DiagnosisSurgery',4),('dict_dim_type','患者与人口学域','DIM_Patient',5),('dict_dim_type','组织架构与人员域','DIM_Organization',6),('dict_dim_type','财务与收费域','DIM_Finance',7),('dict_dim_type','检验与检查域','DIM_LabExam',8),('dict_dim_type','供应链与物资域','DIM_SupplyChain',9),('dict_dim_type','病案与质控域','DIM_MedicalRecord',10),('dict_dim_type','基础治疗与疗程域','DIM_TimeDuration',11),('dict_dim_type','系统权限与安全域','DIM_Security',12),
  ('lineage_node_type','报表','REPORT',1),('lineage_node_type','视图','VIEW',2),('lineage_node_type','存储过程','PROCEDURE',3),('lineage_node_type','数据表','TABLE',4),('lineage_node_type','基础字典表','DICT',5),('lineage_node_type','ETL作业','ETL_JOB',6),('lineage_node_type','CTE','CTE',7),
  ('lineage_mapping_type','直接映射','DIRECT',1),('lineage_mapping_type','计算映射','CALC',2),('lineage_mapping_type','关联映射','JOIN',3),('lineage_mapping_type','过滤映射','FILTER',4),('lineage_mapping_type','聚合映射','AGGREGATE',5),('lineage_mapping_type','函数转换','FUNCTION',6),
  ('lineage_calc_type','算术运算','ARITHMETIC',1),('lineage_calc_type','聚合计算','AGGREGATE',2),('lineage_calc_type','条件判断','CASE',3),('lineage_calc_type','函数转换','FUNCTION',4),('lineage_calc_type','字符串拼接','CONCAT',5),
  ('lineage_parse_status','待解析','PENDING',1),('lineage_parse_status','解析中','PARSING',2),('lineage_parse_status','解析成功','SUCCESS',3),('lineage_parse_status','解析失败','FAILED',4),
  ('lineage_transform_type','直接转换','DIRECT',1),('lineage_transform_type','聚合','AGGREGATE',2),('lineage_transform_type','过滤','FILTER',3),('lineage_transform_type','计算','CALC',4),('lineage_transform_type','去重','DEDUP',5),
  ('lineage_aggregation_type','求和','SUM',1),('lineage_aggregation_type','平均','AVG',2),('lineage_aggregation_type','计数','COUNT',3),('lineage_aggregation_type','最大','MAX',4),('lineage_aggregation_type','最小','MIN',5),
  ('lineage_operator','加','+',1),('lineage_operator','减','-',2),('lineage_operator','乘','*',3),('lineage_operator','除','/',4),('lineage_operator','取模','%',5),('lineage_operator','拼接','||',6),
  ('lineage_layer','应用层','1',1),('lineage_layer','逻辑层','2',2),('lineage_layer','物理层','3',3),('lineage_layer','字典层','4',4),
  ('report_type','报表','REPORT',1),('report_type','视图','VIEW',2),('report_type','存储过程','PROCEDURE',3),('report_type','ETL作业','ETL_JOB',4),('report_type','即席查询','ADHOC',5)
) AS v(type_code, dict_label, dict_value, sort_order)
JOIN dam_sys.sys_dict_type d ON d.dict_type_code = v.type_code
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_dict_data x WHERE x.dict_type_id = d.id AND x.dict_value = v.dict_value);

-- ==================== 完成 ====================
SELECT '初始化数据完成（幂等）' AS result;
