-- =====================================================================
-- v9：左侧菜单“智能助手”更名为“AI 工作台”
-- 仅更新菜单/导航配置，不删除任何数据。
-- =====================================================================
UPDATE dam_sys.sys_menu
SET menu_name = 'AI 工作台',
    updated_at = CURRENT_TIMESTAMP
WHERE path = '/agent'
  AND deleted_at IS NULL;

UPDATE dam_sys.sys_config
SET config_name = 'AI 工作台',
    description = 'AI 工作台导航',
    updated_at = CURRENT_TIMESTAMP
WHERE config_type = 'navigation'
  AND config_value = '/agent';
