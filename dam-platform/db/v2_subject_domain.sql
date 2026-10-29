-- ============================================================================
-- 资产目录「主题域 → 类目」两级改造（幂等，可重复执行）
--
-- 规则：
--   1. dam_asset.asset_category 只允许两层：tree_level=1 为「主题域」，tree_level=2 为「类目」
--   2. 同一张表可在多个主题域下归属，但同一主题域下只能归属一个类目
--      → dam_asset.asset_category_rel(asset_id, subject_domain_id) 唯一
--   3. 不同角色可见不同主题域 → dam_sys.sys_role_subject_domain
--   4. asset_info.category_id 保留为「主分类」，兼容旧逻辑（值仍为类目ID）
-- ============================================================================
BEGIN;

-- ---------- 1. 资产-主题域-类目 归属表 ----------
CREATE TABLE IF NOT EXISTS dam_asset.asset_category_rel (
    id                 BIGSERIAL PRIMARY KEY,
    asset_id           BIGINT      NOT NULL,
    subject_domain_id  BIGINT      NOT NULL,
    category_id        BIGINT      NOT NULL,
    created_at         TIMESTAMP   NOT NULL DEFAULT now(),
    created_by         VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMP   NOT NULL DEFAULT now(),
    updated_by         VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted_at         TIMESTAMP
);

COMMENT ON TABLE  dam_asset.asset_category_rel IS '资产-主题域-类目归属（一表可属多个主题域，同一主题域下唯一类目）';
COMMENT ON COLUMN dam_asset.asset_category_rel.asset_id IS '资产ID（asset_info.id）';
COMMENT ON COLUMN dam_asset.asset_category_rel.subject_domain_id IS '一级主题域ID（asset_category.tree_level=1）';
COMMENT ON COLUMN dam_asset.asset_category_rel.category_id IS '二级类目ID（asset_category.tree_level=2）';

-- 同一资产在同一主题域下只能有一个类目
CREATE UNIQUE INDEX IF NOT EXISTS uk_asset_domain_rel
    ON dam_asset.asset_category_rel (asset_id, subject_domain_id)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_acr_asset ON dam_asset.asset_category_rel (asset_id);
CREATE INDEX IF NOT EXISTS idx_acr_category ON dam_asset.asset_category_rel (category_id);
CREATE INDEX IF NOT EXISTS idx_acr_domain ON dam_asset.asset_category_rel (subject_domain_id);

-- ---------- 2. 角色-主题域可见授权 ----------
CREATE TABLE IF NOT EXISTS dam_sys.sys_role_subject_domain (
    id                 BIGSERIAL PRIMARY KEY,
    role_id            BIGINT      NOT NULL,
    subject_domain_id  BIGINT      NOT NULL,
    created_at         TIMESTAMP   NOT NULL DEFAULT now(),
    created_by         VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMP   NOT NULL DEFAULT now(),
    updated_by         VARCHAR(64) NOT NULL DEFAULT 'system',
    deleted_at         TIMESTAMP
);

COMMENT ON TABLE dam_sys.sys_role_subject_domain IS '角色可查看的资产主题域（未配置的角色默认可见全部）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_role_subject_domain
    ON dam_sys.sys_role_subject_domain (role_id, subject_domain_id)
    WHERE deleted_at IS NULL;

-- ---------- 3. 创建一级主题域：医疗数据主题域 ----------
INSERT INTO dam_asset.asset_category
    (category_code, category_name, parent_id, tree_level, category_path, description, sort_order, status, created_by, updated_by)
SELECT 'MEDICAL_DOMAIN', '医疗数据主题域', NULL, 1, '/医疗数据主题域',
       '医院医疗业务数据资产总主题域', 1, 1, 'system', 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM dam_asset.asset_category c
    WHERE c.category_code = 'MEDICAL_DOMAIN' AND c.deleted_at IS NULL
);

-- ---------- 4. 把原有三个一级分类降为「医疗数据主题域」下的类目 ----------
UPDATE dam_asset.asset_category c
SET parent_id     = d.id,
    tree_level    = 2,
    category_path = '/医疗数据主题域/' || c.category_name,
    updated_at    = now(),
    updated_by    = 'system'
FROM dam_asset.asset_category d
WHERE d.category_code = 'MEDICAL_DOMAIN'
  AND d.deleted_at IS NULL
  AND c.category_code IN ('HOSPITAL', 'CLINICAL', 'MANAGE')
  AND c.deleted_at IS NULL
  AND c.tree_level = 1;

-- ---------- 5. 迁移已有资产归属到关系表 ----------
INSERT INTO dam_asset.asset_category_rel
    (asset_id, subject_domain_id, category_id, created_at, created_by, updated_at, updated_by)
SELECT a.id, c.parent_id, a.category_id, now(), 'system', now(), 'system'
FROM dam_asset.asset_info a
JOIN dam_asset.asset_category c ON c.id = a.category_id AND c.deleted_at IS NULL
WHERE c.parent_id IS NOT NULL
  AND a.deleted_at IS NULL
ON CONFLICT (asset_id, subject_domain_id) WHERE deleted_at IS NULL DO NOTHING;

-- ---------- 6. 默认授权：现有角色均可查看「医疗数据主题域」 ----------
INSERT INTO dam_sys.sys_role_subject_domain
    (role_id, subject_domain_id, created_at, created_by, updated_at, updated_by)
SELECT r.id, d.id, now(), 'system', now(), 'system'
FROM dam_sys.sys_role r
CROSS JOIN dam_asset.asset_category d
WHERE d.category_code = 'MEDICAL_DOMAIN'
  AND d.deleted_at IS NULL
  AND r.deleted_at IS NULL
  AND r.status = 1
ON CONFLICT (role_id, subject_domain_id) WHERE deleted_at IS NULL DO NOTHING;

COMMIT;
