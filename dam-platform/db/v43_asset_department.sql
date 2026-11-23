-- v43: 资产补 department，修复 DamAsset.department 为 exist=false 导致的数据范围 SQL 错误
BEGIN;
ALTER TABLE dam_asset.asset_info
    ADD COLUMN IF NOT EXISTS department varchar(100);

COMMENT ON COLUMN dam_asset.asset_info.department
    IS '资产归属部门；用于角色 DEPARTMENT 数据范围，空值表示未归属';

CREATE INDEX IF NOT EXISTS idx_asset_info_department
    ON dam_asset.asset_info (department);

-- 存量资产按创建人部门回填；无法匹配的保持为空，由数据管家补录
UPDATE dam_asset.asset_info a
   SET department = u.department
  FROM dam_sys.sys_user u
 WHERE a.department IS NULL
   AND a.deleted_at IS NULL
   AND u.deleted_at IS NULL
   AND a.created_by = u.username;
COMMIT;
