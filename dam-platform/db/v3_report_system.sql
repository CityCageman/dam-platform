-- ============================================================================
-- 报表体系（报表即资产）+ 财务/运营主题域与独立角色（幂等，可重复执行）
--
-- 设计要点：
--   1. 报表作为一类资产：asset_info.source_metadata_type='REPORT'，扩展属性存 report_catalog
--   2. 报表权限复用资产主题域：asset_category_rel（报表也写归属）
--   3. 执行方式抽象：SQL / PROC(存储过程) / HTTP / IFRAME / STATIC
--   4. 维度表可扩展：一期内置「业务主题域 / 报表类型 / 更新频率」
-- ============================================================================
BEGIN;

-- ---------- 1. 一级主题域：财务 / 运营 ----------
INSERT INTO dam_asset.asset_category
    (category_code, category_name, parent_id, tree_level, category_path, description, sort_order, status, created_by, updated_by)
SELECT v.code, v.name, NULL, 1, '/' || v.name, v.descr, v.sort_order, 1, 'system', 'system'
FROM (VALUES
    ('FINANCE_DOMAIN', '财务数据主题域', '医院财务收支、成本、预算、结算相关数据', 2),
    ('OPERATION_DOMAIN', '运营数据主题域', '医院门诊、住院、床位、人效等运营管理相关数据', 3)
) AS v(code, name, descr, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM dam_asset.asset_category c
    WHERE c.category_code = v.code AND c.deleted_at IS NULL
);

-- ---------- 2. 二级类目 ----------
INSERT INTO dam_asset.asset_category
    (category_code, category_name, parent_id, tree_level, category_path, description, sort_order, status, created_by, updated_by)
SELECT v.code, v.name, d.id, 2, d.category_path || '/' || v.name, v.descr, v.sort_order, 1, 'system', 'system'
FROM (VALUES
    ('FINANCE_DOMAIN', 'FIN_REVENUE', '收入类', '医疗收入、药品收入、检查检验收入', 1),
    ('FINANCE_DOMAIN', 'FIN_COST', '成本类', '科室成本、耗材成本、人力成本', 2),
    ('FINANCE_DOMAIN', 'FIN_BUDGET', '预算类', '预算编制、预算执行、预算分析', 3),
    ('FINANCE_DOMAIN', 'FIN_SETTLEMENT', '结算类', '医保结算、患者结算、对账', 4),
    ('OPERATION_DOMAIN', 'OPS_OUTPATIENT', '门诊运营', '门诊量、挂号、就诊人次', 1),
    ('OPERATION_DOMAIN', 'OPS_INPATIENT', '住院运营', '入院出院、在院人数、平均住院日', 2),
    ('OPERATION_DOMAIN', 'OPS_BED', '床位效率', '床位使用率、周转次数、空床', 3),
    ('OPERATION_DOMAIN', 'OPS_EFFICIENCY', '人效绩效', '医师工作量、人均产出、绩效', 4)
) AS v(domain_code, code, name, descr, sort_order)
JOIN dam_asset.asset_category d ON d.category_code = v.domain_code AND d.deleted_at IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM dam_asset.asset_category c
    WHERE c.category_code = v.code AND c.deleted_at IS NULL
);

-- ---------- 3. 独立角色：财务人员 / 运营人员 ----------
INSERT INTO dam_sys.sys_role (role_code, role_name, description, status, created_by, updated_by)
SELECT v.code, v.name, v.descr, 1, 'system', 'system'
FROM (VALUES
    ('finance', '财务人员', '仅可查看财务数据主题域下的资产与报表'),
    ('operation', '运营人员', '仅可查看运营数据主题域下的资产与报表')
) AS v(code, name, descr)
WHERE NOT EXISTS (
    SELECT 1 FROM dam_sys.sys_role r WHERE r.role_code = v.code AND r.deleted_at IS NULL
);

-- ---------- 4. 角色可见主题域 ----------
-- 4.1 财务/运营角色只可见各自主题域
INSERT INTO dam_sys.sys_role_subject_domain (role_id, subject_domain_id, created_by, updated_by)
SELECT r.id, d.id, 'system', 'system'
FROM dam_sys.sys_role r
JOIN dam_asset.asset_category d ON d.category_code IN ('FINANCE_DOMAIN', 'OPERATION_DOMAIN') AND d.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND ((r.role_code = 'finance' AND d.category_code = 'FINANCE_DOMAIN')
    OR (r.role_code = 'operation' AND d.category_code = 'OPERATION_DOMAIN'))
ON CONFLICT (role_id, subject_domain_id) WHERE deleted_at IS NULL DO NOTHING;

-- 4.2 管理员/安全/管理层：可见全部三个主题域
INSERT INTO dam_sys.sys_role_subject_domain (role_id, subject_domain_id, created_by, updated_by)
SELECT r.id, d.id, 'system', 'system'
FROM dam_sys.sys_role r
JOIN dam_asset.asset_category d
  ON d.category_code IN ('MEDICAL_DOMAIN', 'FINANCE_DOMAIN', 'OPERATION_DOMAIN') AND d.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND r.role_code IN ('governor', 'security', 'management')
ON CONFLICT (role_id, subject_domain_id) WHERE deleted_at IS NULL DO NOTHING;

-- ---------- 5. 资产类型：新增“报表” ----------
INSERT INTO dam_sys.sys_dict_data (dict_type_id, dict_label, dict_value, sort_order, is_default, status, created_by, updated_by)
SELECT d.id, '报表', 'report', 5, FALSE, 1, 'system', 'system'
FROM dam_sys.sys_dict_type d
WHERE d.dict_type_code = 'asset_type'
  AND NOT EXISTS (
      SELECT 1 FROM dam_sys.sys_dict_data x WHERE x.dict_type_id = d.id AND x.dict_value = 'report'
  );

INSERT INTO dam_sys.sys_config (config_type, config_code, config_name, config_value, description, sort_order, status, created_by, updated_by)
SELECT 'asset_type', 'report', '报表', 'report', '报表类资产（report_catalog）', 5, 1, 'system', 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM dam_sys.sys_config c WHERE c.config_type = 'asset_type' AND c.config_code = 'report' AND c.deleted_at IS NULL
);

-- ---------- 6. 报表维度（可扩展） ----------
CREATE TABLE IF NOT EXISTS dam_asset.report_dimension (
    id          BIGSERIAL PRIMARY KEY,
    dim_code    VARCHAR(64)  NOT NULL,
    dim_name    VARCHAR(100) NOT NULL,
    source_type VARCHAR(20)  NOT NULL DEFAULT 'STATIC',
    source_ref  VARCHAR(100),
    sort_order  BIGINT       NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    created_by  VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by  VARCHAR(64)  NOT NULL DEFAULT 'system',
    deleted_at  TIMESTAMP
);
COMMENT ON TABLE dam_asset.report_dimension IS '报表体系维度定义（CAD: SUBJECT_DOMAIN/REPORT_TYPE/UPDATE_FREQ，后期可扩展）';
COMMENT ON COLUMN dam_asset.report_dimension.source_type IS '维度值来源：CATEGORY-资产主题域树 / DICT-字典 / STATIC-静态项';

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_dimension_code
    ON dam_asset.report_dimension (dim_code) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS dam_asset.report_dimension_item (
    id           BIGSERIAL PRIMARY KEY,
    dimension_id BIGINT      NOT NULL,
    item_code    VARCHAR(64) NOT NULL,
    item_name    VARCHAR(100) NOT NULL,
    sort_order   BIGINT      NOT NULL DEFAULT 0,
    status       SMALLINT    NOT NULL DEFAULT 1,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    created_by   VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at   TIMESTAMP   NOT NULL DEFAULT now(),
    updated_by   VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted_at   TIMESTAMP
);
COMMENT ON TABLE dam_asset.report_dimension_item IS '报表维度值（静态维度使用）';

-- 内置三维度
INSERT INTO dam_asset.report_dimension (dim_code, dim_name, source_type, source_ref, sort_order, status, created_by, updated_by)
SELECT v.code, v.name, v.src, v.ref, v.ord, 1, 'system', 'system'
FROM (VALUES
    ('SUBJECT_DOMAIN', '业务主题域', 'CATEGORY', NULL, 1),
    ('REPORT_TYPE', '报表类型', 'DICT', 'report_type', 2),
    ('UPDATE_FREQ', '更新频率', 'STATIC', NULL, 3)
) AS v(code, name, src, ref, ord)
WHERE NOT EXISTS (
    SELECT 1 FROM dam_asset.report_dimension d WHERE d.dim_code = v.code AND d.deleted_at IS NULL
);

-- 更新频率静态值
INSERT INTO dam_asset.report_dimension_item (dimension_id, item_code, item_name, sort_order, status, created_by, updated_by)
SELECT d.id, v.code, v.name, v.ord, 1, 'system', 'system'
FROM dam_asset.report_dimension d
JOIN (VALUES
    ('DAILY', '日报', 1), ('WEEKLY', '周报', 2), ('MONTHLY', '月报', 3),
    ('QUARTERLY', '季报', 4), ('YEARLY', '年报', 5), ('REALTIME', '实时', 6)
) AS v(code, name, ord) ON TRUE
WHERE d.dim_code = 'UPDATE_FREQ' AND d.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM dam_asset.report_dimension_item x
      WHERE x.dimension_id = d.id AND x.item_code = v.code AND x.deleted_at IS NULL
  );

-- ---------- 7. 报表扩展表 ----------
CREATE TABLE IF NOT EXISTS dam_asset.report_catalog (
    id                BIGSERIAL PRIMARY KEY,
    asset_id          BIGINT       NOT NULL,
    report_code       VARCHAR(128) NOT NULL,
    report_name       VARCHAR(255) NOT NULL,
    report_type_id    VARCHAR(50),
    dimension_codes   JSONB,
    exec_type         VARCHAR(20)  NOT NULL DEFAULT 'SQL',
    data_source_id    BIGINT,
    sql_content       TEXT,
    endpoint_url      VARCHAR(1000),
    param_defs        JSONB,
    result_columns    JSONB,
    cover_image       VARCHAR(500),
    update_frequency  VARCHAR(20),
    owner             VARCHAR(100),
    description       VARCHAR(1000),
    sort_order        BIGINT       NOT NULL DEFAULT 0,
    status            SMALLINT     NOT NULL DEFAULT 1,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    deleted_at        TIMESTAMP
);
COMMENT ON TABLE dam_asset.report_catalog IS '报表资产扩展属性（1:1 asset_info，source_metadata_type=REPORT）';
COMMENT ON COLUMN dam_asset.report_catalog.exec_type IS '执行方式：SQL-普通查询 / PROC-存储过程 / HTTP-接口 / IFRAME-嵌入式页面 / STATIC-静态说明';
COMMENT ON COLUMN dam_asset.report_catalog.dimension_codes IS '业务维度标签（jsonb 数组，如 ["科室","时间"]）';
COMMENT ON COLUMN dam_asset.report_catalog.param_defs IS '参数定义（jsonb 数组，如 [{code,label,type,required}]）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_catalog_asset
    ON dam_asset.report_catalog (asset_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_report_catalog_code
    ON dam_asset.report_catalog (report_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_report_catalog_status ON dam_asset.report_catalog (status);

-- ---------- 8. 菜单：报表体系（挂在“数据资产”目录下） ----------
INSERT INTO dam_sys.sys_menu (menu_name, menu_type, parent_id, tree_level, path, component, icon, sort_order, visible, status, created_by, updated_by)
SELECT '报表体系', 'C', 2, 2, '/reports', 'ReportCenter', 'DataAnalysis', 5, 1, 1, 'system', 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM dam_sys.sys_menu m WHERE m.path = '/reports' AND m.deleted_at IS NULL
);

COMMIT;
