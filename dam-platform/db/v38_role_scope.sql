-- =====================================================================
-- v38：角色化第一阶段（角色数据范围 + 默认驾驶舱）
--
-- 目标：
--   1. dam_sys.sys_role 增加 data_scope / default_cockpit 两个非空字段；
--   2. 用 CHECK 约束把数据范围限定为 ALL / DEPARTMENT / SELF；
--   3. 幂等 upsert 8 个内置角色，并回填已有角色的合理默认值；
--   4. 给 admin 用户绑定 super_admin，同时保留原有 governor 关系。
--
-- 原则：
--   * 不删除任何现有角色、用户-角色关联、角色-菜单/权限关联；
--   * 全部语句可重复执行；已有非法 data_scope 先回填 SELF 再加约束；
--   * default_cockpit 的枚举校验由 dam-auth 的 RoleAdminService 负责，
--     历史数据中无法识别的驾驶舱值不在本脚本中强制覆盖。
--
-- 执行方式：psql -v ON_ERROR_STOP=1 -f db/v38_role_scope.sql
-- =====================================================================

BEGIN;

-- ---------- 1. 扩展角色表 ----------
ALTER TABLE dam_sys.sys_role
    ADD COLUMN IF NOT EXISTS data_scope varchar(16) NOT NULL DEFAULT 'SELF',
    ADD COLUMN IF NOT EXISTS default_cockpit varchar(32) NOT NULL DEFAULT 'business';

COMMENT ON COLUMN dam_sys.sys_role.data_scope IS '数据范围：ALL-全部，DEPARTMENT-本部门，SELF-仅本人';
COMMENT ON COLUMN dam_sys.sys_role.default_cockpit IS '默认驾驶舱编码：super/executive/department/ops/developer/governance/business/security';

-- 历史数据为空或不在枚举内时先回填 SELF，避免添加 CHECK 约束失败
UPDATE dam_sys.sys_role
   SET data_scope = 'SELF'
 WHERE data_scope IS NULL
    OR data_scope NOT IN ('ALL', 'DEPARTMENT', 'SELF');

UPDATE dam_sys.sys_role
   SET default_cockpit = 'business'
 WHERE default_cockpit IS NULL
    OR btrim(default_cockpit) = '';

-- 兼容列已存在但历史上可为空的部署
ALTER TABLE dam_sys.sys_role
    ALTER COLUMN data_scope SET DEFAULT 'SELF',
    ALTER COLUMN data_scope SET NOT NULL,
    ALTER COLUMN default_cockpit SET DEFAULT 'business',
    ALTER COLUMN default_cockpit SET NOT NULL;

-- ---------- 2. 数据范围 CHECK 约束（幂等） ----------
DO $v38$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint c
          JOIN pg_class t ON t.oid = c.conrelid
          JOIN pg_namespace n ON n.oid = t.relnamespace
         WHERE n.nspname = 'dam_sys'
           AND t.relname = 'sys_role'
           AND c.conname = 'ck_sys_role_data_scope'
    ) THEN
        ALTER TABLE dam_sys.sys_role
            ADD CONSTRAINT ck_sys_role_data_scope
            CHECK (data_scope IN ('ALL', 'DEPARTMENT', 'SELF'));
    END IF;
END
$v38$;

-- ---------- 3. upsert 8 个内置角色 ----------
-- 3.1 仅插入不存在的角色（未删除记录内按 role_code 判重）
INSERT INTO dam_sys.sys_role
    (role_code, role_name, description, status, data_scope, default_cockpit, created_by, updated_by)
SELECT v.role_code, v.role_name, v.description, 1, v.data_scope, v.default_cockpit, 'system', 'system'
FROM (VALUES
    ('super_admin',     '超级管理员',   '全量数据、全产品、全菜单',       'ALL',        'super'),
    ('executive',       '院领导',       '全院数据查看与经营决策分析',     'ALL',        'executive'),
    ('department_head', '科主任',       '本科室数据查看与科室运营分析',   'DEPARTMENT', 'department'),
    ('data_ops',        '数据运维',     '数据接入、调度与平台运行维护',   'ALL',        'ops'),
    ('developer',       '数据开发',     '数据开发、血缘梳理与工单处理',   'SELF',       'developer'),
    ('governor',        '数据管家',     '元数据维护、标准制定、质量配置', 'DEPARTMENT', 'governance'),
    ('business',        '业务用户',     '资产浏览、检索申请、数据使用',   'SELF',       'business'),
    ('security',        '安全管理员',   '分级管控、审计日志、合规检查',   'ALL',        'security')
) AS v(role_code, role_name, description, data_scope, default_cockpit)
WHERE NOT EXISTS (
    SELECT 1
      FROM dam_sys.sys_role r
     WHERE r.role_code = v.role_code
       AND r.deleted_at IS NULL
);

-- 3.2 已存在的内置角色同步固定名称、启用状态与角色化配置
UPDATE dam_sys.sys_role r
   SET role_name = v.role_name,
       description = v.description,
       status = 1,
       data_scope = v.data_scope,
       default_cockpit = v.default_cockpit,
       updated_at = CURRENT_TIMESTAMP,
       updated_by = COALESCE(r.updated_by, 'system')
  FROM (VALUES
    ('super_admin',     '超级管理员',   '全量数据、全产品、全菜单',       'ALL',        'super'),
    ('executive',       '院领导',       '全院数据查看与经营决策分析',     'ALL',        'executive'),
    ('department_head', '科主任',       '本科室数据查看与科室运营分析',   'DEPARTMENT', 'department'),
    ('data_ops',        '数据运维',     '数据接入、调度与平台运行维护',   'ALL',        'ops'),
    ('developer',       '数据开发',     '数据开发、血缘梳理与工单处理',   'SELF',       'developer'),
    ('governor',        '数据管家',     '元数据维护、标准制定、质量配置', 'DEPARTMENT', 'governance'),
    ('business',        '业务用户',     '资产浏览、检索申请、数据使用',   'SELF',       'business'),
    ('security',        '安全管理员',   '分级管控、审计日志、合规检查',   'ALL',        'security')
) AS v(role_code, role_name, description, data_scope, default_cockpit)
 WHERE r.role_code = v.role_code
   AND r.deleted_at IS NULL;

-- ---------- 4. 其他已有角色回填合理默认值 ----------
-- 未列出的角色保留建表默认值 SELF/business；不删除、不改状态。
UPDATE dam_sys.sys_role r
   SET data_scope = v.data_scope,
       default_cockpit = v.default_cockpit,
       updated_at = CURRENT_TIMESTAMP,
       updated_by = COALESCE(r.updated_by, 'system')
  FROM (VALUES
    ('admin',     'ALL',        'super'),
    ('management','ALL',        'executive'),
    ('steward',   'DEPARTMENT', 'governance'),
    ('analyst',   'SELF',       'business'),
    ('finance',   'SELF',       'business'),
    ('operation', 'SELF',       'business')
) AS v(role_code, data_scope, default_cockpit)
 WHERE lower(r.role_code) = v.role_code
   AND r.deleted_at IS NULL;

-- ---------- 5. super_admin 全菜单（仅补授权，不删除既有角色-菜单关联） ----------
INSERT INTO dam_sys.sys_role_menu
    (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 1, 'system', 'system'
  FROM dam_sys.sys_role r
 CROSS JOIN dam_sys.sys_menu m
 WHERE r.role_code = 'super_admin'
   AND r.deleted_at IS NULL
   AND m.deleted_at IS NULL
   AND NOT EXISTS (
       SELECT 1
         FROM dam_sys.sys_role_menu x
        WHERE x.role_id = r.id
          AND x.menu_id = m.id
          AND x.deleted_at IS NULL
   );

-- ---------- 6. admin 用户绑定 super_admin（保留原有 governor 等关系） ----------
INSERT INTO dam_sys.sys_user_role
    (user_id, role_id, default_role, status, created_by, updated_by)
SELECT u.id,
       r.id,
       CASE WHEN EXISTS (
           SELECT 1
             FROM dam_sys.sys_user_role y
            WHERE y.user_id = u.id
              AND y.default_role = 'Y'
              AND y.status = 1
              AND y.deleted_at IS NULL
       ) THEN 'N' ELSE 'Y' END,
       1,
       'system',
       'system'
  FROM dam_sys.sys_user u
  JOIN dam_sys.sys_role r
    ON r.role_code = 'super_admin'
   AND r.deleted_at IS NULL
 WHERE u.username = 'admin'
   AND u.deleted_at IS NULL
   AND NOT EXISTS (
       SELECT 1
         FROM dam_sys.sys_user_role x
        WHERE x.user_id = u.id
          AND x.role_id = r.id
          AND x.deleted_at IS NULL
   );

COMMIT;
