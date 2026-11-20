-- v42: 补齐 8 个角色测试账号（密码统一为 Dam@2026，生产必须修改）
BEGIN;

-- executive
INSERT INTO dam_sys.sys_user
  (username, password, password_hash, salt, real_name, email, phone, department, last_login_at, last_login_ip, status, created_by, updated_by)
SELECT 'executive', '$2a$10$X8a8h8wL9m6EM7TM9bl7JeJ7onWd.FdcNGj2wjQGZCVn1X9UzhFhW', '$2a$10$X8a8h8wL9m6EM7TM9bl7JeJ7onWd.FdcNGj2wjQGZCVn1X9UzhFhW', '', '院领导', 'executive@dam.local', '', '院办', CURRENT_TIMESTAMP, '0.0.0.0', 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_user u WHERE u.username = 'executive' AND u.deleted_at IS NULL);
INSERT INTO dam_sys.sys_user_role (user_id, role_id, default_role, status, created_by, updated_by)
SELECT u.id, r.id, 'Y', 1, 'system', 'system'
FROM dam_sys.sys_user u, dam_sys.sys_role r
WHERE u.username='executive' AND r.role_code='executive' AND u.deleted_at IS NULL AND r.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_user_role x WHERE x.user_id=u.id AND x.role_id=r.id AND x.deleted_at IS NULL);

-- department_head
INSERT INTO dam_sys.sys_user
  (username, password, password_hash, salt, real_name, email, phone, department, last_login_at, last_login_ip, status, created_by, updated_by)
SELECT 'dept_head', '$2a$10$La1ckiFlZGiMBe.DbvDwHOQGkbSQF3i8uuQ3Vjyv3cZNwEvqzxPOa', '$2a$10$La1ckiFlZGiMBe.DbvDwHOQGkbSQF3i8uuQ3Vjyv3cZNwEvqzxPOa', '', '科主任', 'dept_head@dam.local', '', '心内科', CURRENT_TIMESTAMP, '0.0.0.0', 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_user u WHERE u.username = 'dept_head' AND u.deleted_at IS NULL);
INSERT INTO dam_sys.sys_user_role (user_id, role_id, default_role, status, created_by, updated_by)
SELECT u.id, r.id, 'Y', 1, 'system', 'system'
FROM dam_sys.sys_user u, dam_sys.sys_role r
WHERE u.username='dept_head' AND r.role_code='department_head' AND u.deleted_at IS NULL AND r.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_user_role x WHERE x.user_id=u.id AND x.role_id=r.id AND x.deleted_at IS NULL);

-- data_ops
INSERT INTO dam_sys.sys_user
  (username, password, password_hash, salt, real_name, email, phone, department, last_login_at, last_login_ip, status, created_by, updated_by)
SELECT 'data_ops', '$2a$10$uh7Fet7eW7ZyZOu3adBA4OZg8fFs3uogoYpQBKrQo2cZF0qMwzi3C', '$2a$10$uh7Fet7eW7ZyZOu3adBA4OZg8fFs3uogoYpQBKrQo2cZF0qMwzi3C', '', '数据运维', 'data_ops@dam.local', '', '信息中心', CURRENT_TIMESTAMP, '0.0.0.0', 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_user u WHERE u.username = 'data_ops' AND u.deleted_at IS NULL);
INSERT INTO dam_sys.sys_user_role (user_id, role_id, default_role, status, created_by, updated_by)
SELECT u.id, r.id, 'Y', 1, 'system', 'system'
FROM dam_sys.sys_user u, dam_sys.sys_role r
WHERE u.username='data_ops' AND r.role_code='data_ops' AND u.deleted_at IS NULL AND r.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_user_role x WHERE x.user_id=u.id AND x.role_id=r.id AND x.deleted_at IS NULL);

-- developer
INSERT INTO dam_sys.sys_user
  (username, password, password_hash, salt, real_name, email, phone, department, last_login_at, last_login_ip, status, created_by, updated_by)
SELECT 'developer', '$2a$10$URzbHnrz5m56lgP5D73bqOllWslwpJQGdbI.sDGgzJurYG0bMQEi.', '$2a$10$URzbHnrz5m56lgP5D73bqOllWslwpJQGdbI.sDGgzJurYG0bMQEi.', '', '数据开发', 'developer@dam.local', '', '信息中心', CURRENT_TIMESTAMP, '0.0.0.0', 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_user u WHERE u.username = 'developer' AND u.deleted_at IS NULL);
INSERT INTO dam_sys.sys_user_role (user_id, role_id, default_role, status, created_by, updated_by)
SELECT u.id, r.id, 'Y', 1, 'system', 'system'
FROM dam_sys.sys_user u, dam_sys.sys_role r
WHERE u.username='developer' AND r.role_code='developer' AND u.deleted_at IS NULL AND r.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_user_role x WHERE x.user_id=u.id AND x.role_id=r.id AND x.deleted_at IS NULL);

-- governor
INSERT INTO dam_sys.sys_user
  (username, password, password_hash, salt, real_name, email, phone, department, last_login_at, last_login_ip, status, created_by, updated_by)
SELECT 'governor', '$2a$10$v9OrJWR3ezPgNhJbnj3TwuE1sXzxKWHlVj6.Bv4r5c3sel/2KEkgO', '$2a$10$v9OrJWR3ezPgNhJbnj3TwuE1sXzxKWHlVj6.Bv4r5c3sel/2KEkgO', '', '数据管家', 'governor@dam.local', '', '数据治理办公室', CURRENT_TIMESTAMP, '0.0.0.0', 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_user u WHERE u.username = 'governor' AND u.deleted_at IS NULL);
INSERT INTO dam_sys.sys_user_role (user_id, role_id, default_role, status, created_by, updated_by)
SELECT u.id, r.id, 'Y', 1, 'system', 'system'
FROM dam_sys.sys_user u, dam_sys.sys_role r
WHERE u.username='governor' AND r.role_code='governor' AND u.deleted_at IS NULL AND r.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_user_role x WHERE x.user_id=u.id AND x.role_id=r.id AND x.deleted_at IS NULL);

-- business
INSERT INTO dam_sys.sys_user
  (username, password, password_hash, salt, real_name, email, phone, department, last_login_at, last_login_ip, status, created_by, updated_by)
SELECT 'business', '$2a$10$7VYgr0oUWZm8gEph6N4mxOlTvrP398FDdbk7X8sOo7twuEIBnvO4a', '$2a$10$7VYgr0oUWZm8gEph6N4mxOlTvrP398FDdbk7X8sOo7twuEIBnvO4a', '', '业务用户', 'business@dam.local', '', '数据分析中心', CURRENT_TIMESTAMP, '0.0.0.0', 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_user u WHERE u.username = 'business' AND u.deleted_at IS NULL);
INSERT INTO dam_sys.sys_user_role (user_id, role_id, default_role, status, created_by, updated_by)
SELECT u.id, r.id, 'Y', 1, 'system', 'system'
FROM dam_sys.sys_user u, dam_sys.sys_role r
WHERE u.username='business' AND r.role_code='business' AND u.deleted_at IS NULL AND r.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_user_role x WHERE x.user_id=u.id AND x.role_id=r.id AND x.deleted_at IS NULL);

-- security
INSERT INTO dam_sys.sys_user
  (username, password, password_hash, salt, real_name, email, phone, department, last_login_at, last_login_ip, status, created_by, updated_by)
SELECT 'security_admin', '$2a$10$oDw6Of3WzAXfdciavRnj.uwoCJeguSHyQf3qSNhoLSU/DGqiVPVby', '$2a$10$oDw6Of3WzAXfdciavRnj.uwoCJeguSHyQf3qSNhoLSU/DGqiVPVby', '', '安全管理员', 'security_admin@dam.local', '', '信息中心', CURRENT_TIMESTAMP, '0.0.0.0', 1, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_user u WHERE u.username = 'security_admin' AND u.deleted_at IS NULL);
INSERT INTO dam_sys.sys_user_role (user_id, role_id, default_role, status, created_by, updated_by)
SELECT u.id, r.id, 'Y', 1, 'system', 'system'
FROM dam_sys.sys_user u, dam_sys.sys_role r
WHERE u.username='security_admin' AND r.role_code='security' AND u.deleted_at IS NULL AND r.deleted_at IS NULL
AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_user_role x WHERE x.user_id=u.id AND x.role_id=r.id AND x.deleted_at IS NULL);

COMMIT;
