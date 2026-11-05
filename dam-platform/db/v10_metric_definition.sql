-- =====================================================================
-- v10：指标中心（指标定义 + 手工填报值）
-- 不修改已有表；新增 dam_asset.metric_definition / metric_value
-- =====================================================================
CREATE TABLE IF NOT EXISTS dam_asset.metric_definition (
    id                bigserial PRIMARY KEY,
    metric_code       varchar(100) NOT NULL,
    metric_name       varchar(200) NOT NULL,
    metric_type       varchar(20) NOT NULL DEFAULT 'COMPUTED',
    business_domain   varchar(100),
    definition        varchar(1000),
    formula           varchar(2000),
    unit              varchar(50),
    data_source_id    bigint,
    source_tables     jsonb,
    sql_expression    text,
    dimensions        jsonb,
    time_grain        varchar(30),
    owner             varchar(100),
    status            smallint NOT NULL DEFAULT 1,
    version           integer NOT NULL DEFAULT 1,
    created_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        varchar(100) NOT NULL DEFAULT 'system',
    updated_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        varchar(100) NOT NULL DEFAULT 'system',
    deleted_at        timestamp
);
COMMENT ON TABLE dam_asset.metric_definition IS '指标定义：MANUAL-手工填报，COMPUTED-系统计算';
CREATE UNIQUE INDEX IF NOT EXISTS uk_metric_definition_code
    ON dam_asset.metric_definition (metric_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_metric_definition_type
    ON dam_asset.metric_definition (metric_type, status);

CREATE TABLE IF NOT EXISTS dam_asset.metric_value (
    id                bigserial PRIMARY KEY,
    metric_id         bigint NOT NULL,
    period            varchar(30) NOT NULL,
    dimension_key     varchar(500) NOT NULL DEFAULT '',
    dimension_values  jsonb,
    metric_value      numeric(30,6),
    note              varchar(500),
    status            smallint NOT NULL DEFAULT 1,
    created_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        varchar(100) NOT NULL DEFAULT 'system',
    updated_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        varchar(100) NOT NULL DEFAULT 'system',
    deleted_at        timestamp
);
COMMENT ON TABLE dam_asset.metric_value IS '手工填报指标值（按期间+维度）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_metric_value_period
    ON dam_asset.metric_value (metric_id, period, dimension_key) WHERE deleted_at IS NULL;

-- 菜单：治理能力 / 指标管理
INSERT INTO dam_sys.sys_menu
    (menu_name, menu_type, parent_id, tree_level, path, icon, sort_order, visible, status, created_by, updated_by)
SELECT '指标管理', 'C', p.id, 2, '/metrics', 'TrendCharts', 5, 1, 1, 'system', 'system'
FROM dam_sys.sys_menu p
WHERE p.menu_name = '治理能力' AND p.parent_id IS NULL AND p.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_menu m WHERE m.path = '/metrics' AND m.deleted_at IS NULL);

-- 导航配置
INSERT INTO dam_sys.sys_config
    (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by)
SELECT 'navigation', 'nav_metrics', '指标管理', '/metrics', '指标管理导航', 12, 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_config WHERE config_type='navigation' AND config_value='/metrics');

-- 角色菜单授权
INSERT INTO dam_sys.sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 1, 'system', 'system'
FROM dam_sys.sys_role r, dam_sys.sys_menu m
WHERE m.path='/metrics' AND m.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.deleted_at IS NULL);
