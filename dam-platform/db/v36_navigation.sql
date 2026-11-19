-- v36: 按产品边界重建 navigation 菜单配置
-- 说明：只替换 dam_sys.sys_config 中 config_type=navigation 的数据；
--       不修改 sys_menu、角色授权和业务数据。当前左侧固定入口为 4 项，
--       其余记录用于模块/产品菜单维护与后续动态菜单。

DELETE FROM dam_sys.sys_config WHERE config_type = 'navigation';

INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_dashboard', '运营看板', '/dashboard', '平台管理/运营看板', 1, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_portal', '统一门户', '/portal', '平台管理/统一门户', 2, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_profile', '个人中心', '/profile', '平台管理/个人中心', 3, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_system_config', '系统配置', '/system-config', '平台管理/系统配置', 4, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_catalog', '资产目录', '/catalog', '数据资产/DAM 资产目录', 5, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_metadata', '元数据管理', '/metadata', '数据资产/DAM 元数据管理', 6, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_integration', '数据集成', '/integration', '数据接入/数据集成', 7, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_data_sources', '数据源管理', '/data-sources', '数据接入/数据源连接', 8, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_models', '模型管理', '/models', '数据处理/数据建模', 9, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_develop', '数据开发', '/develop', '数据处理/数据开发', 10, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_scheduler', '调度中心', '/scheduler', '平台管理/调度中心', 11, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_task_center', '任务中心', '/task-center', '平台管理/任务中心', 12, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_standard', '数据标准', '/standard', '数据治理/数据标准', 13, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_quality', '数据质量', '/quality', '数据治理/数据质量', 14, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_security', '安全管控', '/security', '数据治理/数据安全', 15, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_lifecycle', '生命周期', '/lifecycle', '数据治理/生命周期', 16, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_lineage', '数据血缘', '/lineage', '数据治理/数据血缘', 17, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_data_service', '数据服务', '/data-service', '数据服务/API 服务', 18, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_metrics', '指标管理', '/metrics', '数据分析/指标管理', 19, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_reports', '报表体系', '/reports', '数据分析/报表体系', 20, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_knowledge', '知识中心', '/knowledge', '数据分析/知识中心', 21, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_agent', 'AI 工作台', '/agent', '数据分析/AI 工作台', 22, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO dam_sys.sys_config
  (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by, created_at, updated_at)
VALUES ('navigation', 'nav_operations', '工单中心', '/operations', '平台管理/工单运维', 23, 1, 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 校验：
-- SELECT config_code, config_name, config_value, description, sort_order
-- FROM dam_sys.sys_config WHERE config_type='navigation' AND deleted_at IS NULL
-- ORDER BY sort_order;
