--删除所有Schema
DROP SCHEMA dam_asset CASCADE;
DROP SCHEMA dam_metadata CASCADE;
DROP SCHEMA dam_model CASCADE;
DROP SCHEMA dam_security_management CASCADE;
DROP SCHEMA dam_standard CASCADE;
DROP SCHEMA dam_system CASCADE;
DROP SCHEMA dam_ticket_center CASCADE;


-- 创建所有Schema
CREATE SCHEMA IF NOT EXISTS dam_system;
CREATE SCHEMA IF NOT EXISTS dam_metadata;
CREATE SCHEMA IF NOT EXISTS dam_asset;
CREATE SCHEMA IF NOT EXISTS dam_model;
CREATE SCHEMA IF NOT EXISTS dam_security_management;
CREATE SCHEMA IF NOT EXISTS dam_standard;
CREATE SCHEMA IF NOT EXISTS dam_ticket_center;

--一、系统管理模块（dam_system）
--1. 角色表（sys_role）
-- 删除表（如存在）
DROP TABLE IF EXISTS dam_system.sys_role;

-- 创建表
CREATE TABLE dam_system.sys_role (
                                     id BIGSERIAL PRIMARY KEY,
                                     role_code VARCHAR(30) NOT NULL,
                                     role_name VARCHAR(100) NOT NULL,
                                     description VARCHAR(500),
                                     status SMALLINT NOT NULL DEFAULT 1,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     created_by VARCHAR(100) NOT NULL,
                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_by VARCHAR(100) NOT NULL,
                                     deleted_at TIMESTAMP
);

-- 表注释
COMMENT ON TABLE dam_system.sys_role IS '角色表';

-- 字段注释
COMMENT ON COLUMN dam_system.sys_role.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_role.role_code IS '角色编码';
COMMENT ON COLUMN dam_system.sys_role.role_name IS '角色名称';
COMMENT ON COLUMN dam_system.sys_role.description IS '角色描述';
COMMENT ON COLUMN dam_system.sys_role.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_role.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_role.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_role.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_role.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_role.deleted_at IS '删除时间（软删除）';

-- 索引
CREATE UNIQUE INDEX idx_sys_role_code ON dam_system.sys_role(role_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_role_status ON dam_system.sys_role(status);
--2. 用户表（sys_user）
DROP TABLE IF EXISTS dam_system.sys_user;

CREATE TABLE dam_system.sys_user (
                                     id BIGSERIAL PRIMARY KEY,
                                     username VARCHAR(50) NOT NULL,
                                     password VARCHAR(255) NOT NULL,
                                     real_name VARCHAR(100),
                                     email VARCHAR(100),
                                     phone VARCHAR(20),
                                     department VARCHAR(100) NOT NULL,
                                     avatar VARCHAR(255),
                                     last_login_at TIMESTAMP NOT NULL,
                                     last_login_ip VARCHAR(20) NOT NULL,
                                     status SMALLINT NOT NULL DEFAULT 1,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     created_by VARCHAR(100) NOT NULL,
                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_by VARCHAR(100) NOT NULL,
                                     deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_system.sys_user IS '用户表';

COMMENT ON COLUMN dam_system.sys_user.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_user.username IS '用户名';
COMMENT ON COLUMN dam_system.sys_user.password IS '密码（Bcrypt加密）';
COMMENT ON COLUMN dam_system.sys_user.real_name IS '真实姓名';
COMMENT ON COLUMN dam_system.sys_user.email IS '邮箱';
COMMENT ON COLUMN dam_system.sys_user.phone IS '手机号';
COMMENT ON COLUMN dam_system.sys_user.department IS '所属科室/部门';
COMMENT ON COLUMN dam_system.sys_user.avatar IS '头像';
COMMENT ON COLUMN dam_system.sys_user.last_login_at IS '最后登录时间';
COMMENT ON COLUMN dam_system.sys_user.last_login_ip IS '最后登录IP';
COMMENT ON COLUMN dam_system.sys_user.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_user.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_user.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_user.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_user.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_user.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_sys_user_username ON dam_system.sys_user(username) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_user_status ON dam_system.sys_user(status);
--3. 用户角色关联表（sys_user_role）
DROP TABLE IF EXISTS dam_system.sys_user_role;

CREATE TABLE dam_system.sys_user_role (
                                          id BIGSERIAL PRIMARY KEY,
                                          user_id BIGINT NOT NULL,
                                          role_id BIGINT NOT NULL,
                                          default_role VARCHAR(2) NOT NULL DEFAULT 'N',
                                          status SMALLINT NOT NULL DEFAULT 1,
                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          created_by VARCHAR(100) NOT NULL,
                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_by VARCHAR(100) NOT NULL,
                                          deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_system.sys_user_role IS '用户角色关联表';

COMMENT ON COLUMN dam_system.sys_user_role.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_user_role.user_id IS '用户ID';
COMMENT ON COLUMN dam_system.sys_user_role.role_id IS '角色ID';
COMMENT ON COLUMN dam_system.sys_user_role.default_role IS '是否默认角色：Y-是，N-否';
COMMENT ON COLUMN dam_system.sys_user_role.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_user_role.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_user_role.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_user_role.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_user_role.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_user_role.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_sys_user_role_unique ON dam_system.sys_user_role(user_id, role_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_user_role_user ON dam_system.sys_user_role(user_id);
CREATE INDEX idx_sys_user_role_role ON dam_system.sys_user_role(role_id);
--4. 权限表（sys_permission）
DROP TABLE IF EXISTS dam_system.sys_permission;

CREATE TABLE dam_system.sys_permission (
                                           id BIGSERIAL PRIMARY KEY,
                                           permission_code VARCHAR(50) NOT NULL,
                                           permission_name VARCHAR(100) NOT NULL,
                                           parent_id BIGINT,
                                           type VARCHAR(20) NOT NULL,
                                           path VARCHAR(200) NOT NULL,
                                           icon VARCHAR(100),
                                           sort_order INTEGER,
                                           status SMALLINT NOT NULL DEFAULT 1,
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           created_by VARCHAR(100) NOT NULL,
                                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_by VARCHAR(100) NOT NULL,
                                           deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_system.sys_permission IS '权限表';

COMMENT ON COLUMN dam_system.sys_permission.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_permission.permission_code IS '权限编码';
COMMENT ON COLUMN dam_system.sys_permission.permission_name IS '权限名称';
COMMENT ON COLUMN dam_system.sys_permission.parent_id IS '父权限ID';
COMMENT ON COLUMN dam_system.sys_permission.type IS '类型：menu-菜单，button-按钮，api-接口';
COMMENT ON COLUMN dam_system.sys_permission.path IS '路由路径';
COMMENT ON COLUMN dam_system.sys_permission.icon IS '图标';
COMMENT ON COLUMN dam_system.sys_permission.sort_order IS '排序序号';
COMMENT ON COLUMN dam_system.sys_permission.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_permission.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_permission.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_permission.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_permission.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_permission.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_sys_permission_code ON dam_system.sys_permission(permission_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_permission_parent ON dam_system.sys_permission(parent_id);
CREATE INDEX idx_sys_permission_status ON dam_system.sys_permission(status);
--5. 角色权限关联表（sys_role_permission）
DROP TABLE IF EXISTS dam_system.sys_role_permission;

CREATE TABLE dam_system.sys_role_permission (
                                                id BIGSERIAL PRIMARY KEY,
                                                role_id BIGINT NOT NULL,
                                                permission_id BIGINT NOT NULL,
                                                status SMALLINT NOT NULL DEFAULT 1,
                                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                created_by VARCHAR(100) NOT NULL,
                                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_by VARCHAR(100) NOT NULL,
                                                deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_system.sys_role_permission IS '角色权限关联表';

COMMENT ON COLUMN dam_system.sys_role_permission.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_role_permission.role_id IS '角色ID';
COMMENT ON COLUMN dam_system.sys_role_permission.permission_id IS '权限ID';
COMMENT ON COLUMN dam_system.sys_role_permission.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_role_permission.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_role_permission.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_role_permission.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_role_permission.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_role_permission.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_sys_role_permission_unique ON dam_system.sys_role_permission(role_id, permission_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_role_permission_role ON dam_system.sys_role_permission(role_id);
CREATE INDEX idx_sys_role_permission_permission ON dam_system.sys_role_permission(permission_id);
--6. 系统配置表（sys_config）
DROP TABLE IF EXISTS dam_system.sys_config;

CREATE TABLE dam_system.sys_config (
                                       id BIGSERIAL PRIMARY KEY,
                                       config_type VARCHAR(50) NOT NULL,
                                       config_code VARCHAR(50) NOT NULL,
                                       config_name VARCHAR(100) NOT NULL,
                                       config_value TEXT,
                                       description VARCHAR(500),
                                       sort_order INTEGER NOT NULL DEFAULT 0,
                                       status SMALLINT NOT NULL DEFAULT 1,
                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       created_by VARCHAR(100) NOT NULL,
                                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_by VARCHAR(100) NOT NULL,
                                       deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_system.sys_config IS '系统配置表';

COMMENT ON COLUMN dam_system.sys_config.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_config.config_type IS '配置分组：SYSTEM-系统，NOTIFY-通知，SECURITY-安全，JOB-任务';
COMMENT ON COLUMN dam_system.sys_config.config_code IS '配置键';
COMMENT ON COLUMN dam_system.sys_config.config_name IS '配置名称';
COMMENT ON COLUMN dam_system.sys_config.config_value IS '配置值';
COMMENT ON COLUMN dam_system.sys_config.description IS '配置说明';
COMMENT ON COLUMN dam_system.sys_config.sort_order IS '排序序号';
COMMENT ON COLUMN dam_system.sys_config.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_config.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_config.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_config.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_config.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_config.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_sys_config_code ON dam_system.sys_config(config_type, config_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_config_status ON dam_system.sys_config(status);
--7. 系统菜单表（sys_menu）
DROP TABLE IF EXISTS dam_system.sys_menu;

CREATE TABLE dam_system.sys_menu (
                                     id BIGSERIAL PRIMARY KEY,
                                     menu_name VARCHAR(50) NOT NULL,
                                     menu_type VARCHAR(10) NOT NULL,
                                     parent_id BIGINT,
                                     tree_level INTEGER NOT NULL DEFAULT 1,
                                     path VARCHAR(200),
                                     component VARCHAR(200),
                                     redirect VARCHAR(200),
                                     icon VARCHAR(50),
                                     sort_order INTEGER NOT NULL DEFAULT 0,
                                     visible SMALLINT NOT NULL DEFAULT 1,
                                     status SMALLINT NOT NULL DEFAULT 1,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     created_by VARCHAR(100) NOT NULL,
                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_by VARCHAR(100) NOT NULL,
                                     deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_system.sys_menu IS '系统菜单表';

COMMENT ON COLUMN dam_system.sys_menu.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_menu.menu_name IS '菜单名称';
COMMENT ON COLUMN dam_system.sys_menu.menu_type IS '类型：M-目录，C-菜单，F-按钮';
COMMENT ON COLUMN dam_system.sys_menu.parent_id IS '父菜单ID';
COMMENT ON COLUMN dam_system.sys_menu.tree_level IS '树层级（根为1）';
COMMENT ON COLUMN dam_system.sys_menu.path IS '路由地址';
COMMENT ON COLUMN dam_system.sys_menu.component IS '组件路径';
COMMENT ON COLUMN dam_system.sys_menu.redirect IS '重定向地址';
COMMENT ON COLUMN dam_system.sys_menu.icon IS '图标名称';
COMMENT ON COLUMN dam_system.sys_menu.sort_order IS '排序序号';
COMMENT ON COLUMN dam_system.sys_menu.visible IS '是否显示：0-不显示，1-显示';
COMMENT ON COLUMN dam_system.sys_menu.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_menu.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_menu.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_menu.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_menu.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_menu.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_sys_menu_parent ON dam_system.sys_menu(parent_id);
CREATE INDEX idx_sys_menu_status ON dam_system.sys_menu(status);
--8. 角色菜单关联表（sys_role_menu）
DROP TABLE IF EXISTS dam_system.sys_role_menu;

CREATE TABLE dam_system.sys_role_menu (
                                          id BIGSERIAL PRIMARY KEY,
                                          role_id BIGINT NOT NULL,
                                          menu_id BIGINT NOT NULL,
                                          status SMALLINT NOT NULL DEFAULT 1,
                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          created_by VARCHAR(100) NOT NULL,
                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_by VARCHAR(100) NOT NULL,
                                          deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_system.sys_role_menu IS '角色菜单关联表';

COMMENT ON COLUMN dam_system.sys_role_menu.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_role_menu.role_id IS '角色ID';
COMMENT ON COLUMN dam_system.sys_role_menu.menu_id IS '菜单ID';
COMMENT ON COLUMN dam_system.sys_role_menu.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_role_menu.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_role_menu.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_role_menu.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_role_menu.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_role_menu.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_sys_role_menu_unique ON dam_system.sys_role_menu(role_id, menu_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_role_menu_role ON dam_system.sys_role_menu(role_id);
CREATE INDEX idx_sys_role_menu_menu ON dam_system.sys_role_menu(menu_id);
--9. 通用字典类型表（sys_dict_type）
DROP TABLE IF EXISTS dam_system.sys_dict_type;

CREATE TABLE dam_system.sys_dict_type (
                                          id BIGSERIAL PRIMARY KEY,
                                          dict_type_code VARCHAR(100),
                                          dict_type_name VARCHAR(200),
                                          remark VARCHAR(500),
                                          status SMALLINT NOT NULL DEFAULT 1,
                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          created_by VARCHAR(100) NOT NULL,
                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_by VARCHAR(100) NOT NULL,
                                          deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_system.sys_dict_type IS '通用字典类型表';

COMMENT ON COLUMN dam_system.sys_dict_type.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_dict_type.dict_type_code IS '字典类型编码';
COMMENT ON COLUMN dam_system.sys_dict_type.dict_type_name IS '字典类型名称';
COMMENT ON COLUMN dam_system.sys_dict_type.remark IS '备注';
COMMENT ON COLUMN dam_system.sys_dict_type.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_dict_type.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_dict_type.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_dict_type.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_dict_type.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_dict_type.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_sys_dict_type_code ON dam_system.sys_dict_type(dict_type_code) WHERE deleted_at IS NULL;
--10. 字典数据表（sys_dict_data）
DROP TABLE IF EXISTS dam_system.sys_dict_data;

CREATE TABLE dam_system.sys_dict_data (
                                          id BIGSERIAL PRIMARY KEY,
                                          dict_type_id INTEGER,
                                          dict_label VARCHAR(100),
                                          dict_value VARCHAR(200),
                                          dict_extra VARCHAR(500),
                                          sort_order INTEGER,
                                          is_default BOOLEAN,
                                          remarks VARCHAR(500),
                                          status SMALLINT NOT NULL DEFAULT 1,
                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          created_by VARCHAR(100) NOT NULL,
                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_by VARCHAR(100) NOT NULL,
                                          deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_system.sys_dict_data IS '字典数据表';

COMMENT ON COLUMN dam_system.sys_dict_data.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_dict_data.dict_type_id IS '字典类型ID';
COMMENT ON COLUMN dam_system.sys_dict_data.dict_label IS '字典标签（界面展示名称）';
COMMENT ON COLUMN dam_system.sys_dict_data.dict_value IS '字典值（代码存储值）';
COMMENT ON COLUMN dam_system.sys_dict_data.dict_extra IS '扩展值（如样式类名、颜色值等UI辅助信息）';
COMMENT ON COLUMN dam_system.sys_dict_data.sort_order IS '同级排序序号';
COMMENT ON COLUMN dam_system.sys_dict_data.is_default IS '是否默认值';
COMMENT ON COLUMN dam_system.sys_dict_data.remarks IS '备注';
COMMENT ON COLUMN dam_system.sys_dict_data.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_system.sys_dict_data.created_at IS '创建时间';
COMMENT ON COLUMN dam_system.sys_dict_data.created_by IS '创建人';
COMMENT ON COLUMN dam_system.sys_dict_data.updated_at IS '更新时间';
COMMENT ON COLUMN dam_system.sys_dict_data.updated_by IS '更新人';
COMMENT ON COLUMN dam_system.sys_dict_data.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_sys_dict_data_type ON dam_system.sys_dict_data(dict_type_id);
CREATE INDEX idx_sys_dict_data_status ON dam_system.sys_dict_data(status);
--11. 系统操作审计表（sys_audit_log）
DROP TABLE IF EXISTS dam_system.sys_audit_log;

CREATE TABLE dam_system.sys_audit_log (
                                          id BIGSERIAL PRIMARY KEY,
                                          trace_id VARCHAR(50) NOT NULL,
                                          session_id VARCHAR(64) NOT NULL,
                                          operator_id VARCHAR(64) NOT NULL,
                                          operator_ip VARCHAR(45) NOT NULL,
                                          user_agent TEXT,
                                          module VARCHAR(50) NOT NULL,
                                          operation VARCHAR(100),
                                          operation_type VARCHAR(30),
                                          target_type VARCHAR(30),
                                          target_id VARCHAR(100),
                                          target_name VARCHAR(200),
                                          request_method VARCHAR(10),
                                          request_url VARCHAR(500),
                                          request_params JSONB,
                                          response_status INTEGER,
                                          response_body TEXT,
                                          duration_ms INTEGER,
                                          is_success BOOLEAN,
                                          error_message TEXT,
                                          risk_level VARCHAR(20),
                                          operation_time TIMESTAMP NOT NULL,
                                          created_at TIMESTAMP NOT NULL
);

COMMENT ON TABLE dam_system.sys_audit_log IS '系统操作审计表';

COMMENT ON COLUMN dam_system.sys_audit_log.id IS '自增ID';
COMMENT ON COLUMN dam_system.sys_audit_log.trace_id IS '链路追踪ID';
COMMENT ON COLUMN dam_system.sys_audit_log.session_id IS '会话ID';
COMMENT ON COLUMN dam_system.sys_audit_log.operator_id IS '操作人ID';
COMMENT ON COLUMN dam_system.sys_audit_log.operator_ip IS '操作人IP地址';
COMMENT ON COLUMN dam_system.sys_audit_log.user_agent IS '客户端User-Agent';
COMMENT ON COLUMN dam_system.sys_audit_log.module IS '操作模块';
COMMENT ON COLUMN dam_system.sys_audit_log.operation IS '操作描述';
COMMENT ON COLUMN dam_system.sys_audit_log.operation_type IS '操作类型：LOGIN-登录，LOGOUT-登出，CREATE-新增，UPDATE-修改，DELETE-删除，GRANT-授权，ACCESS-访问';
COMMENT ON COLUMN dam_system.sys_audit_log.target_type IS '目标类型：USER-用户，ROLE-角色，MENU-菜单，CONFIG-配置，DICT-字典';
COMMENT ON COLUMN dam_system.sys_audit_log.target_id IS '目标ID';
COMMENT ON COLUMN dam_system.sys_audit_log.target_name IS '目标名称（冗余）';
COMMENT ON COLUMN dam_system.sys_audit_log.request_method IS '请求方法：GET/POST/PUT/DELETE';
COMMENT ON COLUMN dam_system.sys_audit_log.request_url IS '请求URL';
COMMENT ON COLUMN dam_system.sys_audit_log.request_params IS '请求参数';
COMMENT ON COLUMN dam_system.sys_audit_log.response_status IS 'HTTP响应状态码';
COMMENT ON COLUMN dam_system.sys_audit_log.response_body IS '响应体摘要';
COMMENT ON COLUMN dam_system.sys_audit_log.duration_ms IS '响应耗时（毫秒）';
COMMENT ON COLUMN dam_system.sys_audit_log.is_success IS '是否操作成功';
COMMENT ON COLUMN dam_system.sys_audit_log.error_message IS '异常堆栈摘要';
COMMENT ON COLUMN dam_system.sys_audit_log.risk_level IS '风险等级：LOW-低，MEDIUM-中，HIGH-高，CRITICAL-严重';
COMMENT ON COLUMN dam_system.sys_audit_log.operation_time IS '操作时间';
COMMENT ON COLUMN dam_system.sys_audit_log.created_at IS '创建时间';

CREATE INDEX idx_sys_audit_log_operator ON dam_system.sys_audit_log(operator_id, operation_time DESC);
CREATE INDEX idx_sys_audit_log_time ON dam_system.sys_audit_log(operation_time DESC);
CREATE INDEX idx_sys_audit_log_trace ON dam_system.sys_audit_log(trace_id);
CREATE INDEX idx_sys_audit_log_session ON dam_system.sys_audit_log(session_id);

--二、元数据管理模块（dam_metadata）
--1. 元数据分类表（metadata_category）
DROP TABLE IF EXISTS dam_metadata.metadata_category;

CREATE TABLE dam_metadata.metadata_category (
                                                id BIGSERIAL PRIMARY KEY,
                                                category_name VARCHAR(50) NOT NULL,
                                                category_code VARCHAR(50) NOT NULL,
                                                category_type VARCHAR(20) NOT NULL,
                                                parent_id BIGINT,
                                                tree_level INTEGER NOT NULL,
                                                full_path VARCHAR(500),
                                                icon VARCHAR(500),
                                                description VARCHAR(500),
                                                sort_order INTEGER NOT NULL DEFAULT 0,
                                                status SMALLINT NOT NULL DEFAULT 1,
                                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                created_by VARCHAR(100) NOT NULL,
                                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_by VARCHAR(100) NOT NULL,
                                                deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_category IS '元数据分类表';

COMMENT ON COLUMN dam_metadata.metadata_category.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_category.category_name IS '分类名称（如：技术元数据、业务元数据、管理元数据）';
COMMENT ON COLUMN dam_metadata.metadata_category.category_code IS '分类编码（如：TECHNICAL、BUSINESS、MANAGEMENT），程序枚举引用';
COMMENT ON COLUMN dam_metadata.metadata_category.category_type IS '分类层级类型：ROOT-根分类（一级），SUB-子分类（二级及以下）';
COMMENT ON COLUMN dam_metadata.metadata_category.parent_id IS '父分类ID（根分类的parent_id为NULL）';
COMMENT ON COLUMN dam_metadata.metadata_category.tree_level IS '树层级（根为1，子类依次递增）';
COMMENT ON COLUMN dam_metadata.metadata_category.full_path IS '分类全路径（如：/技术元数据/数据库表），便于前端面包屑展示';
COMMENT ON COLUMN dam_metadata.metadata_category.icon IS '图标名称（前端展示用）';
COMMENT ON COLUMN dam_metadata.metadata_category.description IS '分类描述说明';
COMMENT ON COLUMN dam_metadata.metadata_category.sort_order IS '同级排序序号';
COMMENT ON COLUMN dam_metadata.metadata_category.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_category.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_category.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_category.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_category.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_category.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_metadata_category_code ON dam_metadata.metadata_category(category_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_metadata_category_parent ON dam_metadata.metadata_category(parent_id);
CREATE INDEX idx_metadata_category_type ON dam_metadata.metadata_category(category_type);
--2. 数据源表（metadata_datasource）
DROP TABLE IF EXISTS dam_metadata.metadata_datasource;

CREATE TABLE dam_metadata.metadata_datasource (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  ds_name VARCHAR(100) NOT NULL,
                                                  ds_code VARCHAR(100) NOT NULL,
                                                  ds_type VARCHAR(50) NOT NULL,
                                                  ds_adapter VARCHAR(100) NOT NULL,
                                                  ds_ip VARCHAR(20),
                                                  ds_port VARCHAR(10),
                                                  jdbc_url VARCHAR(200) NOT NULL,
                                                  username VARCHAR(100) NOT NULL,
                                                  password VARCHAR(100) NOT NULL,
                                                  driver_class VARCHAR(200) NOT NULL,
                                                  connection_props VARCHAR(200),
                                                  description VARCHAR(200),
                                                  status SMALLINT NOT NULL DEFAULT 1,
                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  created_by VARCHAR(100) NOT NULL,
                                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  updated_by VARCHAR(100) NOT NULL,
                                                  deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_datasource IS '数据源表';

COMMENT ON COLUMN dam_metadata.metadata_datasource.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_datasource.ds_name IS '数据源名称';
COMMENT ON COLUMN dam_metadata.metadata_datasource.ds_code IS '数据源代码';
COMMENT ON COLUMN dam_metadata.metadata_datasource.ds_type IS '数据源类型';
COMMENT ON COLUMN dam_metadata.metadata_datasource.ds_adapter IS '数据源适配器';
COMMENT ON COLUMN dam_metadata.metadata_datasource.ds_ip IS '数据源IP';
COMMENT ON COLUMN dam_metadata.metadata_datasource.ds_port IS '数据源端口';
COMMENT ON COLUMN dam_metadata.metadata_datasource.jdbc_url IS 'JDBC连接URL';
COMMENT ON COLUMN dam_metadata.metadata_datasource.username IS '用户名';
COMMENT ON COLUMN dam_metadata.metadata_datasource.password IS '密码（加密存储）';
COMMENT ON COLUMN dam_metadata.metadata_datasource.driver_class IS '驱动类';
COMMENT ON COLUMN dam_metadata.metadata_datasource.connection_props IS '额外连接参数';
COMMENT ON COLUMN dam_metadata.metadata_datasource.description IS '描述';
COMMENT ON COLUMN dam_metadata.metadata_datasource.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_datasource.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_datasource.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_datasource.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_datasource.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_datasource.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_metadata_datasource_code ON dam_metadata.metadata_datasource(ds_code) WHERE deleted_at IS NULL;
--3. 技术元数据-表（metadata_table）
DROP TABLE IF EXISTS dam_metadata.metadata_table;

CREATE TABLE dam_metadata.metadata_table (
                                             id BIGSERIAL PRIMARY KEY,
                                             datasource_id BIGINT NOT NULL,
                                             schema_id VARCHAR(100) NOT NULL,
                                             table_name VARCHAR(100) NOT NULL,
                                             table_comment VARCHAR(500),
                                             table_type VARCHAR(50) NOT NULL,
                                             row_count VARCHAR(100),
                                             table_size VARCHAR(100),
                                             created_time TIMESTAMP NOT NULL,
                                             last_modified TIMESTAMP NOT NULL,
                                             last_sync_time TIMESTAMP NOT NULL,
                                             status SMALLINT NOT NULL DEFAULT 1,
                                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             created_by VARCHAR(100) NOT NULL,
                                             updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             updated_by VARCHAR(100) NOT NULL,
                                             deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_table IS '技术元数据-表';

COMMENT ON COLUMN dam_metadata.metadata_table.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_table.datasource_id IS '所属数据源ID';
COMMENT ON COLUMN dam_metadata.metadata_table.schema_id IS 'Schema ID';
COMMENT ON COLUMN dam_metadata.metadata_table.table_name IS '表名';
COMMENT ON COLUMN dam_metadata.metadata_table.table_comment IS '表注释';
COMMENT ON COLUMN dam_metadata.metadata_table.table_type IS '表类型：TABLE-表，VIEW-视图';
COMMENT ON COLUMN dam_metadata.metadata_table.row_count IS '行数';
COMMENT ON COLUMN dam_metadata.metadata_table.table_size IS '表大小（字节）';
COMMENT ON COLUMN dam_metadata.metadata_table.created_time IS '表创建时间';
COMMENT ON COLUMN dam_metadata.metadata_table.last_modified IS '最后修改时间';
COMMENT ON COLUMN dam_metadata.metadata_table.last_sync_time IS '最后同步时间';
COMMENT ON COLUMN dam_metadata.metadata_table.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_table.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_table.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_table.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_table.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_table.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_metadata_table_datasource ON dam_metadata.metadata_table(datasource_id);
CREATE INDEX idx_metadata_table_name ON dam_metadata.metadata_table(table_name);
--4. 技术元数据-字段（metadata_column）
DROP TABLE IF EXISTS dam_metadata.metadata_column;

CREATE TABLE dam_metadata.metadata_column (
                                              id BIGSERIAL PRIMARY KEY,
                                              datasource_id BIGINT NOT NULL,
                                              schema_id VARCHAR(100) NOT NULL,
                                              table_id BIGINT NOT NULL,
                                              column_name VARCHAR(100),
                                              column_type VARCHAR(100),
                                              column_length INTEGER,
                                              is_nullable BOOLEAN NOT NULL DEFAULT TRUE,
                                              is_primary_key BOOLEAN,
                                              is_foreign_key BOOLEAN,
                                              default_value VARCHAR(300),
                                              column_comment VARCHAR(200),
                                              security_level VARCHAR(20),
                                              sensitive_type VARCHAR(50),
                                              reference_table_name VARCHAR(200),
                                              ordinal_position INTEGER,
                                              status SMALLINT NOT NULL DEFAULT 1,
                                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              created_by VARCHAR(100) NOT NULL,
                                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              updated_by VARCHAR(100) NOT NULL,
                                              deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_column IS '技术元数据-字段';

COMMENT ON COLUMN dam_metadata.metadata_column.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_column.datasource_id IS '所属数据源ID';
COMMENT ON COLUMN dam_metadata.metadata_column.schema_id IS 'Schema ID';
COMMENT ON COLUMN dam_metadata.metadata_column.table_id IS '所属表ID';
COMMENT ON COLUMN dam_metadata.metadata_column.column_name IS '字段名';
COMMENT ON COLUMN dam_metadata.metadata_column.column_type IS '字段类型';
COMMENT ON COLUMN dam_metadata.metadata_column.column_length IS '字段长度';
COMMENT ON COLUMN dam_metadata.metadata_column.is_nullable IS '是否可空';
COMMENT ON COLUMN dam_metadata.metadata_column.is_primary_key IS '是否主键';
COMMENT ON COLUMN dam_metadata.metadata_column.is_foreign_key IS '是否外键';
COMMENT ON COLUMN dam_metadata.metadata_column.default_value IS '默认值';
COMMENT ON COLUMN dam_metadata.metadata_column.column_comment IS '字段注释';
COMMENT ON COLUMN dam_metadata.metadata_column.security_level IS '安全等级：public-公开，internal-内部，confidential-内部敏感，restricted-重要数据，critical-核心数据';
COMMENT ON COLUMN dam_metadata.metadata_column.sensitive_type IS '敏感类型（字典 sensitive_type，如身份证号/手机号/姓名）';
COMMENT ON COLUMN dam_metadata.metadata_column.reference_table_name IS '外键/指针引用表名（同步字段时从 JDBC getImportedKeys 获取，供血缘识别）';
COMMENT ON COLUMN dam_metadata.metadata_column.ordinal_position IS '字段顺序';
COMMENT ON COLUMN dam_metadata.metadata_column.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_column.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_column.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_column.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_column.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_column.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_metadata_column_table ON dam_metadata.metadata_column(table_id);
CREATE INDEX idx_metadata_column_name ON dam_metadata.metadata_column(column_name);
--5. 技术元数据-模式（metadata_schema）
DROP TABLE IF EXISTS dam_metadata.metadata_schema;

CREATE TABLE dam_metadata.metadata_schema (
                                              id BIGSERIAL PRIMARY KEY,
                                              datasource_id BIGINT NOT NULL,
                                              schema_name VARCHAR(50),
                                              schema_comment VARCHAR(500),
                                              schema_type VARCHAR(50),
                                              schema_owner VARCHAR(100),
                                              environment VARCHAR(50),
                                              location VARCHAR(500),
                                              properties VARCHAR(200),
                                              status SMALLINT NOT NULL DEFAULT 1,
                                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              created_by VARCHAR(100) NOT NULL,
                                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              updated_by VARCHAR(100) NOT NULL,
                                              deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_schema IS '技术元数据-模式';

COMMENT ON COLUMN dam_metadata.metadata_schema.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_schema.datasource_id IS '所属数据源ID';
COMMENT ON COLUMN dam_metadata.metadata_schema.schema_name IS 'Schema名称';
COMMENT ON COLUMN dam_metadata.metadata_schema.schema_comment IS 'Schema注释';
COMMENT ON COLUMN dam_metadata.metadata_schema.schema_type IS 'Schema类型（如：DATABASE、CATALOG、VIRTUAL）';
COMMENT ON COLUMN dam_metadata.metadata_schema.schema_owner IS 'Schema负责人ID';
COMMENT ON COLUMN dam_metadata.metadata_schema.environment IS '环境标识：PROD-生产，TEST-测试，DEV-开发';
COMMENT ON COLUMN dam_metadata.metadata_schema.location IS '物理存储路径（如HDFS路径、S3路径）';
COMMENT ON COLUMN dam_metadata.metadata_schema.properties IS '扩展属性（JSON格式，如字符集、排序规则等）';
COMMENT ON COLUMN dam_metadata.metadata_schema.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_schema.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_schema.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_schema.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_schema.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_schema.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_metadata_schema_datasource ON dam_metadata.metadata_schema(datasource_id);
--6. 业务元数据表（metadata_business）
DROP TABLE IF EXISTS dam_metadata.metadata_business;

CREATE TABLE dam_metadata.metadata_business (
                                                id BIGSERIAL PRIMARY KEY,
                                                business_code VARCHAR(50) NOT NULL,
                                                business_name VARCHAR(200) NOT NULL,
                                                business_definition TEXT,
                                                business_owner_id BIGINT,
                                                data_domain_id BIGINT,
                                                business_domain_id BIGINT,
                                                business_process VARCHAR(100),
                                                data_steward_id BIGINT,
                                                criticality_level VARCHAR(50),
                                                data_quality_rules_id BIGINT,
                                                tags TEXT[],
                                                status SMALLINT NOT NULL DEFAULT 1,
                                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                created_by VARCHAR(100) NOT NULL,
                                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_by VARCHAR(100) NOT NULL,
                                                deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_business IS '业务元数据表';

COMMENT ON COLUMN dam_metadata.metadata_business.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_business.business_code IS '业务代码';
COMMENT ON COLUMN dam_metadata.metadata_business.business_name IS '业务名称';
COMMENT ON COLUMN dam_metadata.metadata_business.business_definition IS '业务定义/描述';
COMMENT ON COLUMN dam_metadata.metadata_business.business_owner_id IS '业务负责人ID';
COMMENT ON COLUMN dam_metadata.metadata_business.data_domain_id IS '数据域ID';
COMMENT ON COLUMN dam_metadata.metadata_business.business_domain_id IS '业务域ID';
COMMENT ON COLUMN dam_metadata.metadata_business.business_process IS '业务流程';
COMMENT ON COLUMN dam_metadata.metadata_business.data_steward_id IS '数据管家ID（负责数据质量日常监控与标准执行）';
COMMENT ON COLUMN dam_metadata.metadata_business.criticality_level IS '业务重要性：HIGH-高，MEDIUM-中，LOW-低';
COMMENT ON COLUMN dam_metadata.metadata_business.data_quality_rules_id IS '数据质量规则ID';
COMMENT ON COLUMN dam_metadata.metadata_business.tags IS '标签数组';
COMMENT ON COLUMN dam_metadata.metadata_business.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_business.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_business.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_business.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_business.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_business.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_metadata_business_code ON dam_metadata.metadata_business(business_code) WHERE deleted_at IS NULL;
--7. 业务-表关联映射表（metadata_business_table_rel）
DROP TABLE IF EXISTS dam_metadata.metadata_business_table_rel;

CREATE TABLE dam_metadata.metadata_business_table_rel (
                                                          id BIGSERIAL PRIMARY KEY,
                                                          business_id BIGINT NOT NULL,
                                                          table_id BIGINT NOT NULL,
                                                          mapping_type_id VARCHAR(200) NOT NULL,
                                                          mapping_comment TEXT NOT NULL,
                                                          status SMALLINT NOT NULL DEFAULT 1,
                                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                          created_by VARCHAR(100) NOT NULL,
                                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                          updated_by VARCHAR(100) NOT NULL,
                                                          deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_business_table_rel IS '业务-表关联映射表';

COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.business_id IS '业务实体ID';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.table_id IS '技术元数据表ID';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.mapping_type_id IS '映射类型ID';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.mapping_comment IS '映射说明';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_business_table_rel.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_metadata_business_table_rel_unique ON dam_metadata.metadata_business_table_rel(business_id, table_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_metadata_business_table_rel_business ON dam_metadata.metadata_business_table_rel(business_id);
CREATE INDEX idx_metadata_business_table_rel_table ON dam_metadata.metadata_business_table_rel(table_id);
--8. 管理元数据表（metadata_management）
DROP TABLE IF EXISTS dam_metadata.metadata_management;

CREATE TABLE dam_metadata.metadata_management (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  table_id BIGINT NOT NULL,
                                                  governance_status_id VARCHAR(30) NOT NULL,
                                                  lifecycle_status_id VARCHAR(20),
                                                  retention_days BIGINT,
                                                  source_system_id BIGINT NOT NULL,
                                                  etl_workflow VARCHAR(200),
                                                  last_accessed_at TIMESTAMP,
                                                  daily_query_avg BIGINT,
                                                  security_classification VARCHAR(20),
                                                  data_verification_status VARCHAR(20),
                                                  registration_batch_id VARCHAR(50),
                                                  last_ddl_time TIMESTAMP,
                                                  audit_remark TEXT,
                                                  extend_props JSONB,
                                                  status SMALLINT NOT NULL DEFAULT 1,
                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  created_by VARCHAR(100) NOT NULL,
                                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  updated_by VARCHAR(100) NOT NULL,
                                                  deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_management IS '管理元数据表';

COMMENT ON COLUMN dam_metadata.metadata_management.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_management.table_id IS '关联技术元数据表ID';
COMMENT ON COLUMN dam_metadata.metadata_management.governance_status_id IS '治理状态ID：TO_BE_GOVERNED-待治理，IN_PROGRESS-治理中，GOVERNED-已治理，IGNORED-已忽略';
COMMENT ON COLUMN dam_metadata.metadata_management.lifecycle_status_id IS '生命周期状态ID：ACTIVE-活跃，COLD-冷数据，ARCHIVED-已归档，PURGED-已清理';
COMMENT ON COLUMN dam_metadata.metadata_management.retention_days IS '数据保留天数';
COMMENT ON COLUMN dam_metadata.metadata_management.source_system_id IS '数据来源系统ID';
COMMENT ON COLUMN dam_metadata.metadata_management.etl_workflow IS 'ETL/数据集成工作流名称';
COMMENT ON COLUMN dam_metadata.metadata_management.last_accessed_at IS '最后被访问/查询的时间';
COMMENT ON COLUMN dam_metadata.metadata_management.daily_query_avg IS '日均查询次数';
COMMENT ON COLUMN dam_metadata.metadata_management.security_classification IS '安全分级ID：PUBLIC-公开，INTERNAL-内部，CONFIDENTIAL-机密，RESTRICTED-高度机密';
COMMENT ON COLUMN dam_metadata.metadata_management.data_verification_status IS '数据验证状态ID：UNVERIFIED-未验证，VERIFIED-已验证，INCONSISTENT-不一致';
COMMENT ON COLUMN dam_metadata.metadata_management.registration_batch_id IS '注册批次ID';
COMMENT ON COLUMN dam_metadata.metadata_management.last_ddl_time IS '表结构变更时间';
COMMENT ON COLUMN dam_metadata.metadata_management.audit_remark IS '审计备注';
COMMENT ON COLUMN dam_metadata.metadata_management.extend_props IS '扩展属性';
COMMENT ON COLUMN dam_metadata.metadata_management.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_management.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_management.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_management.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_management.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_management.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_metadata_management_table ON dam_metadata.metadata_management(table_id) WHERE deleted_at IS NULL;
--9. 来源系统表（source_system）
--sql
DROP TABLE IF EXISTS dam_metadata.source_system;

CREATE TABLE dam_metadata.source_system (
                                            id BIGSERIAL PRIMARY KEY,
                                            system_code VARCHAR(100) NOT NULL,
                                            system_name VARCHAR(200) NOT NULL,
                                            description VARCHAR(500),
                                            status SMALLINT NOT NULL DEFAULT 1,
                                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            created_by VARCHAR(100) NOT NULL,
                                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            updated_by VARCHAR(100) NOT NULL,
                                            deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.source_system IS '来源系统表';

COMMENT ON COLUMN dam_metadata.source_system.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.source_system.system_code IS '系统代码';
COMMENT ON COLUMN dam_metadata.source_system.system_name IS '系统名称';
COMMENT ON COLUMN dam_metadata.source_system.description IS '描述';
COMMENT ON COLUMN dam_metadata.source_system.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.source_system.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.source_system.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.source_system.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.source_system.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.source_system.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_source_system_code ON dam_metadata.source_system(system_code) WHERE deleted_at IS NULL;
--10. 元数据采集记录表（metadata_collection）
--sql
DROP TABLE IF EXISTS dam_metadata.metadata_collection;

CREATE TABLE dam_metadata.metadata_collection (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  datasource_id INTEGER NOT NULL,
                                                  schema_id INTEGER NOT NULL,
                                                  job_name VARCHAR(200),
                                                  job_type VARCHAR(100) NOT NULL,
                                                  start_time TIMESTAMP,
                                                  end_time TIMESTAMP,
                                                  total_tables INTEGER,
                                                  total_columns INTEGER,
                                                  success_count INTEGER,
                                                  fail_count INTEGER,
                                                  error_message VARCHAR(500),
                                                  detail_log JSONB,
                                                  status SMALLINT NOT NULL DEFAULT 1,
                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  created_by VARCHAR(100) NOT NULL,
                                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  updated_by VARCHAR(100) NOT NULL,
                                                  deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_collection IS '元数据采集记录表';

COMMENT ON COLUMN dam_metadata.metadata_collection.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_collection.datasource_id IS '数据源ID';
COMMENT ON COLUMN dam_metadata.metadata_collection.schema_id IS 'Schema ID';
COMMENT ON COLUMN dam_metadata.metadata_collection.job_name IS '任务名称';
COMMENT ON COLUMN dam_metadata.metadata_collection.job_type IS '任务类型';
COMMENT ON COLUMN dam_metadata.metadata_collection.start_time IS '开始时间';
COMMENT ON COLUMN dam_metadata.metadata_collection.end_time IS '结束时间';
COMMENT ON COLUMN dam_metadata.metadata_collection.total_tables IS '采集总表数';
COMMENT ON COLUMN dam_metadata.metadata_collection.total_columns IS '采集总字段数';
COMMENT ON COLUMN dam_metadata.metadata_collection.success_count IS '成功数量';
COMMENT ON COLUMN dam_metadata.metadata_collection.fail_count IS '失败数量';
COMMENT ON COLUMN dam_metadata.metadata_collection.error_message IS '错误日志';
COMMENT ON COLUMN dam_metadata.metadata_collection.detail_log IS '详细日志';
COMMENT ON COLUMN dam_metadata.metadata_collection.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_collection.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_collection.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_collection.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_collection.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_collection.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_metadata_collection_datasource ON dam_metadata.metadata_collection(datasource_id);
--11. 元数据版本表（metadata_version）
--sql
DROP TABLE IF EXISTS dam_metadata.metadata_version;

CREATE TABLE dam_metadata.metadata_version (
                                               id BIGSERIAL PRIMARY KEY,
                                               datasource_id INTEGER NOT NULL,
                                               schema_id INTEGER NOT NULL,
                                               table_id INTEGER NOT NULL,
                                               version_number VARCHAR(50) NOT NULL,
                                               change_type VARCHAR(100) NOT NULL,
                                               change_summary TEXT NOT NULL,
                                               snapshot_data JSONB NOT NULL,
                                               diff_summary JSONB,
                                               changed_by VARCHAR(100) NOT NULL,
                                               changed_at TIMESTAMP NOT NULL,
                                               status SMALLINT NOT NULL DEFAULT 1,
                                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               created_by VARCHAR(100) NOT NULL,
                                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               updated_by VARCHAR(100) NOT NULL,
                                               deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_version IS '元数据版本表';

COMMENT ON COLUMN dam_metadata.metadata_version.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_version.datasource_id IS '数据源ID';
COMMENT ON COLUMN dam_metadata.metadata_version.schema_id IS 'Schema ID';
COMMENT ON COLUMN dam_metadata.metadata_version.table_id IS '表ID';
COMMENT ON COLUMN dam_metadata.metadata_version.version_number IS '版本号';
COMMENT ON COLUMN dam_metadata.metadata_version.change_type IS '变更类型';
COMMENT ON COLUMN dam_metadata.metadata_version.change_summary IS '变更摘要';
COMMENT ON COLUMN dam_metadata.metadata_version.snapshot_data IS '版本快照';
COMMENT ON COLUMN dam_metadata.metadata_version.diff_summary IS '差异摘要';
COMMENT ON COLUMN dam_metadata.metadata_version.changed_by IS '变更人';
COMMENT ON COLUMN dam_metadata.metadata_version.changed_at IS '变更时间';
COMMENT ON COLUMN dam_metadata.metadata_version.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_version.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_version.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_version.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_version.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_version.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_metadata_version_table ON dam_metadata.metadata_version(table_id);
--12. 数据血缘关系表（metadata_lineage）
--sql
DROP TABLE IF EXISTS dam_metadata.metadata_lineage;

CREATE TABLE dam_metadata.metadata_lineage (
                                               id BIGSERIAL PRIMARY KEY,
                                               source_type VARCHAR(50) NOT NULL,
                                               source_id INTEGER NOT NULL,
                                               target_type VARCHAR(50) NOT NULL,
                                               target_id INTEGER NOT NULL,
                                               relation_type VARCHAR(50) NOT NULL,
                                               transform_rule TEXT NOT NULL,
                                               dependency_level INTEGER NOT NULL,
                                               source_system VARCHAR(50) NOT NULL,
                                               collection_id INTEGER NOT NULL,
                                               discovered_by VARCHAR(100),
                                               status SMALLINT NOT NULL DEFAULT 1,
                                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               created_by VARCHAR(100) NOT NULL,
                                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               updated_by VARCHAR(100) NOT NULL,
                                               deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_lineage IS '数据血缘关系表';

COMMENT ON COLUMN dam_metadata.metadata_lineage.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_lineage.source_type IS '源节点类型';
COMMENT ON COLUMN dam_metadata.metadata_lineage.source_id IS '源节点ID';
COMMENT ON COLUMN dam_metadata.metadata_lineage.target_type IS '目标节点类型';
COMMENT ON COLUMN dam_metadata.metadata_lineage.target_id IS '目标节点ID';
COMMENT ON COLUMN dam_metadata.metadata_lineage.relation_type IS '关系类型';
COMMENT ON COLUMN dam_metadata.metadata_lineage.transform_rule IS '转换规则';
COMMENT ON COLUMN dam_metadata.metadata_lineage.dependency_level IS '依赖层级';
COMMENT ON COLUMN dam_metadata.metadata_lineage.source_system IS '来源系统标识';
COMMENT ON COLUMN dam_metadata.metadata_lineage.collection_id IS '关联采集记录ID';
COMMENT ON COLUMN dam_metadata.metadata_lineage.discovered_by IS '发现方式：PARSER-自动解析，MANUAL-手动，IMPORT-导入';
COMMENT ON COLUMN dam_metadata.metadata_lineage.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_lineage.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_lineage.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_lineage.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_lineage.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_lineage.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_metadata_lineage_source ON dam_metadata.metadata_lineage(source_type, source_id);
CREATE INDEX idx_metadata_lineage_target ON dam_metadata.metadata_lineage(target_type, target_id);
--13. 质量规则表（metadata_quality_rule）
--sql
DROP TABLE IF EXISTS dam_metadata.metadata_quality_rule;

CREATE TABLE dam_metadata.metadata_quality_rule (
                                                    id BIGSERIAL PRIMARY KEY,
                                                    rule_code VARCHAR(50) NOT NULL,
                                                    rule_type VARCHAR(100) NOT NULL,
                                                    rule_level VARCHAR(50) NOT NULL,
                                                    target_table_id INTEGER NOT NULL,
                                                    target_column VARCHAR(100),
                                                    rule_expression TEXT,
                                                    expect_value VARCHAR(200),
                                                    severity VARCHAR(50),
                                                    is_scheduled BOOLEAN,
                                                    cron_expression VARCHAR(100),
                                                    description VARCHAR(500),
                                                    status SMALLINT NOT NULL DEFAULT 1,
                                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    created_by VARCHAR(100) NOT NULL,
                                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    updated_by VARCHAR(100) NOT NULL,
                                                    deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_quality_rule IS '质量规则表';

COMMENT ON COLUMN dam_metadata.metadata_quality_rule.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.rule_code IS '规则编码';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.rule_type IS '规则名称';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.rule_level IS '级别';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.target_table_id IS '目标表ID';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.target_column IS '目标字段名（空为全表）';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.rule_expression IS '规则表达式或自定义SQL';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.expect_value IS '期望值（如 > 0.95）';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.severity IS '严重程度';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.is_scheduled IS '是否定时执行';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.cron_expression IS '定时表达式';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.description IS '规则描述';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_quality_rule.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_metadata_quality_rule_code ON dam_metadata.metadata_quality_rule(rule_code) WHERE deleted_at IS NULL;
--14. 质量检测结果表（metadata_quality_check_result）
--sql
DROP TABLE IF EXISTS dam_metadata.metadata_quality_check_result;

CREATE TABLE dam_metadata.metadata_quality_check_result (
                                                            id BIGSERIAL PRIMARY KEY,
                                                            rule_id INTEGER,
                                                            check_time TIMESTAMP,
                                                            total_count INTEGER,
                                                            pass_count INTEGER,
                                                            fail_count INTEGER,
                                                            pass_rate DECIMAL(5,2),
                                                            result_status VARCHAR(50),
                                                            fail_samples JSONB,
                                                            error_detail TEXT,
                                                            executed_by VARCHAR(100),
                                                            status SMALLINT NOT NULL DEFAULT 1,
                                                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                            created_by VARCHAR(100) NOT NULL,
                                                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                            updated_by VARCHAR(100) NOT NULL,
                                                            deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_quality_check_result IS '质量检测结果表';

COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.rule_id IS '规则ID';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.check_time IS '检测时间';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.total_count IS '总记录数';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.pass_count IS '通过记录数';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.fail_count IS '不通过记录数';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.pass_rate IS '通过率';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.result_status IS '结果状态：PASS-通过，WARN-警告，FAIL-失败';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.fail_samples IS '失败样例';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.error_detail IS '错误详情';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.executed_by IS '执行人';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_quality_check_result.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_metadata_quality_check_rule ON dam_metadata.metadata_quality_check_result(rule_id);
--15. 生命周期策略表（metadata_lifecycle_policy）
--sql
DROP TABLE IF EXISTS dam_metadata.metadata_lifecycle_policy;

CREATE TABLE dam_metadata.metadata_lifecycle_policy (
                                                        id BIGSERIAL PRIMARY KEY,
                                                        policy_code VARCHAR(50),
                                                        policy_name VARCHAR(100),
                                                        target_type VARCHAR(50),
                                                        target_id INTEGER,
                                                        hot_days INTEGER,
                                                        cold_days INTEGER,
                                                        archive_days INTEGER,
                                                        purge_days INTEGER,
                                                        cold_storage_location VARCHAR(500),
                                                        archive_storage_location VARCHAR(500),
                                                        archive_format VARCHAR(50),
                                                        auto_execute BOOLEAN,
                                                        last_execute_time TIMESTAMP,
                                                        description VARCHAR(500),
                                                        status SMALLINT NOT NULL DEFAULT 1,
                                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        created_by VARCHAR(100) NOT NULL,
                                                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        updated_by VARCHAR(100) NOT NULL,
                                                        deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_lifecycle_policy IS '生命周期策略表';

COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.policy_code IS '策略代码';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.policy_name IS '策略名';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.target_type IS '目标类型';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.target_id IS '目标ID';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.hot_days IS '热数据保留天数';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.cold_days IS '冷数据保留天数';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.archive_days IS '归档触发天数';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.purge_days IS '清理触发天数';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.cold_storage_location IS '冷存储路径';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.archive_storage_location IS '归档存储路径';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.archive_format IS '归档格式';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.auto_execute IS '是否自动执行';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.last_execute_time IS '最后执行时间';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.description IS '策略描述';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_lifecycle_policy.deleted_at IS '删除时间（软删除）';
--16. 数据销毁记录表（metadata_destruction_record）
--sql
DROP TABLE IF EXISTS dam_metadata.metadata_destruction_record;

CREATE TABLE dam_metadata.metadata_destruction_record (
                                                          id BIGSERIAL PRIMARY KEY,
                                                          destruction_no VARCHAR(50),
                                                          target_type VARCHAR(50),
                                                          target_id INTEGER,
                                                          target_name VARCHAR(100),
                                                          destruction_reason TEXT,
                                                          destruction_scope VARCHAR(100),
                                                          filter_condition TEXT,
                                                          affected_rows INTEGER,
                                                          destruction_method VARCHAR(50),
                                                          requester_id VARCHAR(50),
                                                          approver_id VARCHAR(50),
                                                          executor_id VARCHAR(50),
                                                          requested_at TIMESTAMP,
                                                          approved_at TIMESTAMP,
                                                          executed_at TIMESTAMP,
                                                          remark VARCHAR(500),
                                                          status SMALLINT NOT NULL DEFAULT 1,
                                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                          created_by VARCHAR(100) NOT NULL,
                                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                          updated_by VARCHAR(100) NOT NULL,
                                                          deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_metadata.metadata_destruction_record IS '数据销毁记录表';

COMMENT ON COLUMN dam_metadata.metadata_destruction_record.id IS '自增ID';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.destruction_no IS '销毁单号';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.target_type IS '目标类型';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.target_id IS '目标ID';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.target_name IS '目标名称';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.destruction_reason IS '销毁理由';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.destruction_scope IS '范围';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.filter_condition IS '筛选条件';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.affected_rows IS '影响行数';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.destruction_method IS '销毁方法';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.requester_id IS '申请人ID';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.approver_id IS '审批人ID';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.executor_id IS '执行人ID';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.requested_at IS '申请时间';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.approved_at IS '审批时间';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.executed_at IS '执行时间';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.remark IS '备注';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.created_at IS '创建时间';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.created_by IS '创建人';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.updated_at IS '更新时间';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.updated_by IS '更新人';
COMMENT ON COLUMN dam_metadata.metadata_destruction_record.deleted_at IS '删除时间（软删除）';

--三、数据资产目录模块（dam_asset）
--1. 资产分类表（asset_category）
--sql
DROP TABLE IF EXISTS dam_asset.asset_category;

CREATE TABLE dam_asset.asset_category (
                                          id BIGSERIAL PRIMARY KEY,
                                          category_code VARCHAR(50) NOT NULL,
                                          category_name VARCHAR(100) NOT NULL,
                                          parent_id BIGINT,
                                          tree_level BIGINT NOT NULL,
                                          category_path VARCHAR(500) NOT NULL,
                                          description VARCHAR(500),
                                          sort_order BIGINT NOT NULL,
                                          status SMALLINT NOT NULL DEFAULT 1,
                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          created_by VARCHAR(100) NOT NULL,
                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_by VARCHAR(100) NOT NULL,
                                          deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_asset.asset_category IS '资产分类表';

COMMENT ON COLUMN dam_asset.asset_category.id IS '自增ID';
COMMENT ON COLUMN dam_asset.asset_category.category_code IS '分类编码';
COMMENT ON COLUMN dam_asset.asset_category.category_name IS '分类名称';
COMMENT ON COLUMN dam_asset.asset_category.parent_id IS '父分类ID';
COMMENT ON COLUMN dam_asset.asset_category.tree_level IS '树层级';
COMMENT ON COLUMN dam_asset.asset_category.category_path IS '分类全路径';
COMMENT ON COLUMN dam_asset.asset_category.description IS '分类描述';
COMMENT ON COLUMN dam_asset.asset_category.sort_order IS '排序序号';
COMMENT ON COLUMN dam_asset.asset_category.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_asset.asset_category.created_at IS '创建时间';
COMMENT ON COLUMN dam_asset.asset_category.created_by IS '创建人';
COMMENT ON COLUMN dam_asset.asset_category.updated_at IS '更新时间';
COMMENT ON COLUMN dam_asset.asset_category.updated_by IS '更新人';
COMMENT ON COLUMN dam_asset.asset_category.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_asset_category_code ON dam_asset.asset_category(category_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_asset_category_parent ON dam_asset.asset_category(parent_id);
--2. 资产基本信息表（asset_info）
DROP TABLE IF EXISTS dam_asset.asset_info;

CREATE TABLE dam_asset.asset_info (
                                      id BIGSERIAL PRIMARY KEY,
                                      asset_code VARCHAR(100) NOT NULL,
                                      asset_name VARCHAR(200) NOT NULL,
                                      asset_type_id INTEGER NOT NULL,
                                      category_id INTEGER NOT NULL,
                                      description TEXT,
                                      business_owner_id INTEGER,
                                      tech_owner_id INTEGER,
                                      data_owner_id INTEGER,
                                      source_system_id INTEGER,
                                      data_source_id INTEGER,
                                      source_metadata_type VARCHAR(50),
                                      source_metadata_id INTEGER,
                                      storage_location VARCHAR(500),
                                      row_count BIGINT,
                                      storage_size BIGINT,
                                      quality_score DECIMAL(5,2),
                                      popularity INTEGER,
                                      published_status SMALLINT,
                                      published_at TIMESTAMP,
                                      status SMALLINT NOT NULL DEFAULT 1,
                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      created_by VARCHAR(100) NOT NULL,
                                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_by VARCHAR(100) NOT NULL,
                                      deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_asset.asset_info IS '资产基本信息表';

COMMENT ON COLUMN dam_asset.asset_info.id IS '自增ID';
COMMENT ON COLUMN dam_asset.asset_info.asset_code IS '资产编码';
COMMENT ON COLUMN dam_asset.asset_info.asset_name IS '资产名称';
COMMENT ON COLUMN dam_asset.asset_info.asset_type_id IS '资产类型ID';
COMMENT ON COLUMN dam_asset.asset_info.category_id IS '所属分类ID';
COMMENT ON COLUMN dam_asset.asset_info.description IS '资产描述';
COMMENT ON COLUMN dam_asset.asset_info.business_owner_id IS '业务负责人ID';
COMMENT ON COLUMN dam_asset.asset_info.tech_owner_id IS '技术负责人ID';
COMMENT ON COLUMN dam_asset.asset_info.data_owner_id IS '数据负责人ID';
COMMENT ON COLUMN dam_asset.asset_info.source_system_id IS '来源系统ID';
COMMENT ON COLUMN dam_asset.asset_info.data_source_id IS '数据源ID';
COMMENT ON COLUMN dam_asset.asset_info.source_metadata_type IS '元数据实体类型：TABLE-表，FILE-文件，API-接口，REPORT-报表';
COMMENT ON COLUMN dam_asset.asset_info.source_metadata_id IS '元数据实体ID（根据source_metadata_type关联不同元数据表）';
COMMENT ON COLUMN dam_asset.asset_info.storage_location IS '存储位置';
COMMENT ON COLUMN dam_asset.asset_info.row_count IS '数据行数';
COMMENT ON COLUMN dam_asset.asset_info.storage_size IS '存储大小（字节）';
COMMENT ON COLUMN dam_asset.asset_info.quality_score IS '质量评分';
COMMENT ON COLUMN dam_asset.asset_info.popularity IS '热度（访问次数）';
COMMENT ON COLUMN dam_asset.asset_info.published_status IS '发布状态：1-已发布，2-草稿，3-已下线';
COMMENT ON COLUMN dam_asset.asset_info.published_at IS '发布时间';
COMMENT ON COLUMN dam_asset.asset_info.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_asset.asset_info.created_at IS '创建时间';
COMMENT ON COLUMN dam_asset.asset_info.created_by IS '创建人';
COMMENT ON COLUMN dam_asset.asset_info.updated_at IS '更新时间';
COMMENT ON COLUMN dam_asset.asset_info.updated_by IS '更新人';
COMMENT ON COLUMN dam_asset.asset_info.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_asset_info_code ON dam_asset.asset_info(asset_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_asset_info_category ON dam_asset.asset_info(category_id);
CREATE INDEX idx_asset_info_source_metadata ON dam_asset.asset_info(source_metadata_type, source_metadata_id);
--3. 资产评价表（asset_rating）
DROP TABLE IF EXISTS dam_asset.asset_rating;

CREATE TABLE dam_asset.asset_rating (
                                        id BIGSERIAL PRIMARY KEY,
                                        asset_id BIGINT NOT NULL,
                                        rating_score INTEGER NOT NULL,
                                        rating_comment TEXT NOT NULL,
                                        rating_dimension VARCHAR(50) NOT NULL,
                                        user_id VARCHAR(64) NOT NULL,
                                        status SMALLINT NOT NULL DEFAULT 1,
                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        created_by VARCHAR(100) NOT NULL,
                                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_by VARCHAR(100) NOT NULL,
                                        deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_asset.asset_rating IS '资产评价表';

COMMENT ON COLUMN dam_asset.asset_rating.id IS '自增ID';
COMMENT ON COLUMN dam_asset.asset_rating.asset_id IS '资产ID';
COMMENT ON COLUMN dam_asset.asset_rating.rating_score IS '资产评分（1~5）';
COMMENT ON COLUMN dam_asset.asset_rating.rating_comment IS '评价内容';
COMMENT ON COLUMN dam_asset.asset_rating.rating_dimension IS '评价维度';
COMMENT ON COLUMN dam_asset.asset_rating.user_id IS '评价人ID';
COMMENT ON COLUMN dam_asset.asset_rating.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_asset.asset_rating.created_at IS '创建时间';
COMMENT ON COLUMN dam_asset.asset_rating.created_by IS '创建人';
COMMENT ON COLUMN dam_asset.asset_rating.updated_at IS '更新时间';
COMMENT ON COLUMN dam_asset.asset_rating.updated_by IS '更新人';
COMMENT ON COLUMN dam_asset.asset_rating.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_asset_rating_asset ON dam_asset.asset_rating(asset_id);
--4. 资产使用日志表（asset_usage_log）
--sql
DROP TABLE IF EXISTS dam_asset.asset_usage_log;

CREATE TABLE dam_asset.asset_usage_log (
                                           id BIGSERIAL PRIMARY KEY,
                                           asset_id INTEGER NOT NULL,
                                           user_id VARCHAR(50) NOT NULL,
                                           action_type VARCHAR(50) NOT NULL,
                                           action_detail JSONB NOT NULL,
                                           ip_address VARCHAR(50) NOT NULL,
                                           user_agent TEXT,
                                           status SMALLINT NOT NULL DEFAULT 1,
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           created_by VARCHAR(100) NOT NULL,
                                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_by VARCHAR(100) NOT NULL,
                                           deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_asset.asset_usage_log IS '资产使用日志表';

COMMENT ON COLUMN dam_asset.asset_usage_log.id IS '自增ID';
COMMENT ON COLUMN dam_asset.asset_usage_log.asset_id IS '资产ID';
COMMENT ON COLUMN dam_asset.asset_usage_log.user_id IS '用户ID';
COMMENT ON COLUMN dam_asset.asset_usage_log.action_type IS '操作类型';
COMMENT ON COLUMN dam_asset.asset_usage_log.action_detail IS '操作详情';
COMMENT ON COLUMN dam_asset.asset_usage_log.ip_address IS 'IP';
COMMENT ON COLUMN dam_asset.asset_usage_log.user_agent IS '用户代理';
COMMENT ON COLUMN dam_asset.asset_usage_log.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_asset.asset_usage_log.created_at IS '创建时间';
COMMENT ON COLUMN dam_asset.asset_usage_log.created_by IS '创建人';
COMMENT ON COLUMN dam_asset.asset_usage_log.updated_at IS '更新时间';
COMMENT ON COLUMN dam_asset.asset_usage_log.updated_by IS '更新人';
COMMENT ON COLUMN dam_asset.asset_usage_log.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_asset_usage_log_asset ON dam_asset.asset_usage_log(asset_id);
--5. 标签定义表（asset_tag）
--sql
DROP TABLE IF EXISTS dam_asset.asset_tag;

CREATE TABLE dam_asset.asset_tag (
                                     id BIGSERIAL PRIMARY KEY,
                                     tag_code VARCHAR(100) NOT NULL,
                                     tag_name VARCHAR(200) NOT NULL,
                                     tag_color VARCHAR(20) NOT NULL,
                                     tag_scope VARCHAR(20) NOT NULL,
                                     tag_type VARCHAR(20) NOT NULL,
                                     description VARCHAR(200),
                                     usage_count INTEGER,
                                     status SMALLINT NOT NULL DEFAULT 1,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     created_by VARCHAR(100) NOT NULL,
                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_by VARCHAR(100) NOT NULL,
                                     deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_asset.asset_tag IS '标签定义表';

COMMENT ON COLUMN dam_asset.asset_tag.id IS '自增ID';
COMMENT ON COLUMN dam_asset.asset_tag.tag_code IS '标签代码';
COMMENT ON COLUMN dam_asset.asset_tag.tag_name IS '标签名称';
COMMENT ON COLUMN dam_asset.asset_tag.tag_color IS '标签颜色';
COMMENT ON COLUMN dam_asset.asset_tag.tag_scope IS '标签范围：PUBLIC-公共标签，PRIVATE-私有标签';
COMMENT ON COLUMN dam_asset.asset_tag.tag_type IS '标签类型：SYSTEM-系统预置，CUSTOM-用户自定义';
COMMENT ON COLUMN dam_asset.asset_tag.description IS '描述';
COMMENT ON COLUMN dam_asset.asset_tag.usage_count IS '引用次数';
COMMENT ON COLUMN dam_asset.asset_tag.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_asset.asset_tag.created_at IS '创建时间';
COMMENT ON COLUMN dam_asset.asset_tag.created_by IS '创建人';
COMMENT ON COLUMN dam_asset.asset_tag.updated_at IS '更新时间';
COMMENT ON COLUMN dam_asset.asset_tag.updated_by IS '更新人';
COMMENT ON COLUMN dam_asset.asset_tag.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_asset_tag_code ON dam_asset.asset_tag(tag_code) WHERE deleted_at IS NULL;
--6. 资产标签关联表（asset_tag_rel）
--sql
DROP TABLE IF EXISTS dam_asset.asset_tag_rel;

CREATE TABLE dam_asset.asset_tag_rel (
                                         id BIGSERIAL PRIMARY KEY,
                                         asset_id INTEGER NOT NULL,
                                         tag_id INTEGER NOT NULL,
                                         status SMALLINT NOT NULL DEFAULT 1,
                                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         created_by VARCHAR(100) NOT NULL,
                                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_by VARCHAR(100) NOT NULL,
                                         deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_asset.asset_tag_rel IS '资产标签关联表';

COMMENT ON COLUMN dam_asset.asset_tag_rel.id IS '自增ID';
COMMENT ON COLUMN dam_asset.asset_tag_rel.asset_id IS '资产ID';
COMMENT ON COLUMN dam_asset.asset_tag_rel.tag_id IS '标签ID';
COMMENT ON COLUMN dam_asset.asset_tag_rel.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_asset.asset_tag_rel.created_at IS '创建时间';
COMMENT ON COLUMN dam_asset.asset_tag_rel.created_by IS '创建人';
COMMENT ON COLUMN dam_asset.asset_tag_rel.updated_at IS '更新时间';
COMMENT ON COLUMN dam_asset.asset_tag_rel.updated_by IS '更新人';
COMMENT ON COLUMN dam_asset.asset_tag_rel.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_asset_tag_rel_unique ON dam_asset.asset_tag_rel(asset_id, tag_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_asset_tag_rel_asset ON dam_asset.asset_tag_rel(asset_id);
CREATE INDEX idx_asset_tag_rel_tag ON dam_asset.asset_tag_rel(tag_id);
--7. 资产收藏表（asset_favorite）
--sql
DROP TABLE IF EXISTS dam_asset.asset_favorite;

CREATE TABLE dam_asset.asset_favorite (
                                          id BIGSERIAL PRIMARY KEY,
                                          asset_id INTEGER NOT NULL,
                                          user_id INTEGER NOT NULL,
                                          folder_name VARCHAR(200) NOT NULL,
                                          favorite_time TIMESTAMP NOT NULL,
                                          status SMALLINT NOT NULL DEFAULT 1,
                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          created_by VARCHAR(100) NOT NULL,
                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_by VARCHAR(100) NOT NULL,
                                          deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_asset.asset_favorite IS '资产收藏表';

COMMENT ON COLUMN dam_asset.asset_favorite.id IS '自增ID';
COMMENT ON COLUMN dam_asset.asset_favorite.asset_id IS '资产ID';
COMMENT ON COLUMN dam_asset.asset_favorite.user_id IS '用户ID';
COMMENT ON COLUMN dam_asset.asset_favorite.folder_name IS '收藏夹名称';
COMMENT ON COLUMN dam_asset.asset_favorite.favorite_time IS '收藏时间';
COMMENT ON COLUMN dam_asset.asset_favorite.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_asset.asset_favorite.created_at IS '创建时间';
COMMENT ON COLUMN dam_asset.asset_favorite.created_by IS '创建人';
COMMENT ON COLUMN dam_asset.asset_favorite.updated_at IS '更新时间';
COMMENT ON COLUMN dam_asset.asset_favorite.updated_by IS '更新人';
COMMENT ON COLUMN dam_asset.asset_favorite.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_asset_favorite_unique ON dam_asset.asset_favorite(asset_id, user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_asset_favorite_user ON dam_asset.asset_favorite(user_id);

--四、模型管理模块（dam_model）
--1. 模型主题域表（model_domain）
--sql
DROP TABLE IF EXISTS dam_model.model_domain;

CREATE TABLE dam_model.model_domain (
                                        id BIGSERIAL PRIMARY KEY,
                                        domain_code VARCHAR(100) NOT NULL,
                                        domain_name VARCHAR(200) NOT NULL,
                                        parent_id INTEGER,
                                        description TEXT,
                                        sort_order INTEGER NOT NULL,
                                        status SMALLINT NOT NULL DEFAULT 1,
                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        created_by VARCHAR(100) NOT NULL,
                                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_by VARCHAR(100) NOT NULL,
                                        deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_model.model_domain IS '模型主题域表';

COMMENT ON COLUMN dam_model.model_domain.id IS '自增ID';
COMMENT ON COLUMN dam_model.model_domain.domain_code IS '主题域代码';
COMMENT ON COLUMN dam_model.model_domain.domain_name IS '主题域名称';
COMMENT ON COLUMN dam_model.model_domain.parent_id IS '父主题域ID';
COMMENT ON COLUMN dam_model.model_domain.description IS '描述';
COMMENT ON COLUMN dam_model.model_domain.sort_order IS '排序序号';
COMMENT ON COLUMN dam_model.model_domain.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_model.model_domain.created_at IS '创建时间';
COMMENT ON COLUMN dam_model.model_domain.created_by IS '创建人';
COMMENT ON COLUMN dam_model.model_domain.updated_at IS '更新时间';
COMMENT ON COLUMN dam_model.model_domain.updated_by IS '更新人';
COMMENT ON COLUMN dam_model.model_domain.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_model_domain_code ON dam_model.model_domain(domain_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_model_domain_parent ON dam_model.model_domain(parent_id);
--2. 维度表（model_dimension）
--sql
DROP TABLE IF EXISTS dam_model.model_dimension;

CREATE TABLE dam_model.model_dimension (
                                           id BIGSERIAL PRIMARY KEY,
                                           domain_id INTEGER NOT NULL,
                                           dimension_code VARCHAR(100) NOT NULL,
                                           dimension_name VARCHAR(200) NOT NULL,
                                           dimension_type_id INTEGER,
                                           source_table_id INTEGER,
                                           description TEXT,
                                           status SMALLINT NOT NULL DEFAULT 1,
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           created_by VARCHAR(100) NOT NULL,
                                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_by VARCHAR(100) NOT NULL,
                                           deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_model.model_dimension IS '维度表';

COMMENT ON COLUMN dam_model.model_dimension.id IS '自增ID';
COMMENT ON COLUMN dam_model.model_dimension.domain_id IS '所属主题域ID';
COMMENT ON COLUMN dam_model.model_dimension.dimension_code IS '维度代码';
COMMENT ON COLUMN dam_model.model_dimension.dimension_name IS '维度名称';
COMMENT ON COLUMN dam_model.model_dimension.dimension_type_id IS '维度类型ID';
COMMENT ON COLUMN dam_model.model_dimension.source_table_id IS '源表ID';
COMMENT ON COLUMN dam_model.model_dimension.description IS '描述';
COMMENT ON COLUMN dam_model.model_dimension.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_model.model_dimension.created_at IS '创建时间';
COMMENT ON COLUMN dam_model.model_dimension.created_by IS '创建人';
COMMENT ON COLUMN dam_model.model_dimension.updated_at IS '更新时间';
COMMENT ON COLUMN dam_model.model_dimension.updated_by IS '更新人';
COMMENT ON COLUMN dam_model.model_dimension.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_model_dimension_code ON dam_model.model_dimension(dimension_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_model_dimension_domain ON dam_model.model_dimension(domain_id);
--3. 事实表（model_fact）
DROP TABLE IF EXISTS dam_model.model_fact;

CREATE TABLE dam_model.model_fact (
                                      id BIGSERIAL PRIMARY KEY,
                                      domain_id INTEGER NOT NULL,
                                      fact_code VARCHAR(100) NOT NULL,
                                      fact_name VARCHAR(200) NOT NULL,
                                      source_table_id INTEGER,
                                      granularity VARCHAR(100),
                                      description TEXT,
                                      status SMALLINT NOT NULL DEFAULT 1,
                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      created_by VARCHAR(100) NOT NULL,
                                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_by VARCHAR(100) NOT NULL,
                                      deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_model.model_fact IS '事实表';

COMMENT ON COLUMN dam_model.model_fact.id IS '自增ID';
COMMENT ON COLUMN dam_model.model_fact.domain_id IS '所属主题域ID';
COMMENT ON COLUMN dam_model.model_fact.fact_code IS '事实代码';
COMMENT ON COLUMN dam_model.model_fact.fact_name IS '事实名称';
COMMENT ON COLUMN dam_model.model_fact.source_table_id IS '源表ID';
COMMENT ON COLUMN dam_model.model_fact.granularity IS '粒度描述';
COMMENT ON COLUMN dam_model.model_fact.description IS '描述';
COMMENT ON COLUMN dam_model.model_fact.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_model.model_fact.created_at IS '创建时间';
COMMENT ON COLUMN dam_model.model_fact.created_by IS '创建人';
COMMENT ON COLUMN dam_model.model_fact.updated_at IS '更新时间';
COMMENT ON COLUMN dam_model.model_fact.updated_by IS '更新人';
COMMENT ON COLUMN dam_model.model_fact.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_model_fact_code ON dam_model.model_fact(fact_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_model_fact_domain ON dam_model.model_fact(domain_id);
--4. 模型字段映射表（model_column_mapping）
--sql
DROP TABLE IF EXISTS dam_model.model_column_mapping;

CREATE TABLE dam_model.model_column_mapping (
                                                id BIGSERIAL PRIMARY KEY,
                                                model_id INTEGER,
                                                model_type VARCHAR(50),
                                                source_table_id INTEGER,
                                                source_column_id INTEGER,
                                                target_column_name VARCHAR(100),
                                                target_column_type VARCHAR(50),
                                                is_primary_key INTEGER,
                                                is_foreign_key INTEGER,
                                                ref_dimension_id INTEGER,
                                                transform_rule VARCHAR(500),
                                                sort_order INTEGER,
                                                status SMALLINT NOT NULL DEFAULT 1,
                                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                created_by VARCHAR(100) NOT NULL,
                                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_by VARCHAR(100) NOT NULL,
                                                deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_model.model_column_mapping IS '模型字段映射表';

COMMENT ON COLUMN dam_model.model_column_mapping.id IS '自增ID';
COMMENT ON COLUMN dam_model.model_column_mapping.model_id IS '模型ID';
COMMENT ON COLUMN dam_model.model_column_mapping.model_type IS '模型类型：FACT-事实，DIMENSION-维度';
COMMENT ON COLUMN dam_model.model_column_mapping.source_table_id IS '源表ID';
COMMENT ON COLUMN dam_model.model_column_mapping.source_column_id IS '源字段ID';
COMMENT ON COLUMN dam_model.model_column_mapping.target_column_name IS '目标字段';
COMMENT ON COLUMN dam_model.model_column_mapping.target_column_type IS '目标字段类型';
COMMENT ON COLUMN dam_model.model_column_mapping.is_primary_key IS '是否主键：0-否，1-是';
COMMENT ON COLUMN dam_model.model_column_mapping.is_foreign_key IS '是否外键：0-否，1-是';
COMMENT ON COLUMN dam_model.model_column_mapping.ref_dimension_id IS '引用维度ID';
COMMENT ON COLUMN dam_model.model_column_mapping.transform_rule IS '转换规则';
COMMENT ON COLUMN dam_model.model_column_mapping.sort_order IS '排序序号';
COMMENT ON COLUMN dam_model.model_column_mapping.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_model.model_column_mapping.created_at IS '创建时间';
COMMENT ON COLUMN dam_model.model_column_mapping.created_by IS '创建人';
COMMENT ON COLUMN dam_model.model_column_mapping.updated_at IS '更新时间';
COMMENT ON COLUMN dam_model.model_column_mapping.updated_by IS '更新人';
COMMENT ON COLUMN dam_model.model_column_mapping.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_model_column_mapping_model ON dam_model.model_column_mapping(model_id, model_type);

--五、安全管控模块（dam_security_management）
--1. 敏感数据规则表（security_sensitive_rule）
--sql
DROP TABLE IF EXISTS dam_security_management.security_sensitive_rule;

CREATE TABLE dam_security_management.security_sensitive_rule (
                                                                 id BIGSERIAL PRIMARY KEY,
                                                                 rule_code VARCHAR(100),
                                                                 rule_name VARCHAR(200),
                                                                 sensitivity_level VARCHAR(20),
                                                                 rule_type VARCHAR(50),
                                                                 rule_pattern VARCHAR(200),
                                                                 dictionary_id INTEGER,
                                                                 description TEXT,
                                                                 priority VARCHAR(20),
                                                                 status SMALLINT NOT NULL DEFAULT 1,
                                                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                 created_by VARCHAR(100) NOT NULL,
                                                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                 updated_by VARCHAR(100) NOT NULL,
                                                                 deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_security_management.security_sensitive_rule IS '敏感数据规则表';

COMMENT ON COLUMN dam_security_management.security_sensitive_rule.id IS '自增ID';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.rule_code IS '规则代码';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.rule_name IS '规则名称';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.sensitivity_level IS '敏感等级';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.rule_type IS '规则类型';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.rule_pattern IS '匹配模式';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.dictionary_id IS '关联词典ID';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.description IS '描述';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.priority IS '优先级';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.created_at IS '创建时间';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.created_by IS '创建人';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.updated_at IS '更新时间';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.updated_by IS '更新人';
COMMENT ON COLUMN dam_security_management.security_sensitive_rule.deleted_at IS '删除时间（软删除）';
--2. 敏感数据发现结果表（security_sensitive_discovery）
DROP TABLE IF EXISTS dam_security_management.security_sensitive_discovery;

CREATE TABLE dam_security_management.security_sensitive_discovery (
                                                                      id BIGSERIAL PRIMARY KEY,
                                                                      column_id INTEGER NOT NULL,
                                                                      rule_id INTEGER NOT NULL,
                                                                      sensitivity_level_id INTEGER NOT NULL,
                                                                      confidence DECIMAL(5,2),
                                                                      discovery_time TIMESTAMP,
                                                                      status SMALLINT NOT NULL DEFAULT 1,
                                                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                      created_by VARCHAR(100) NOT NULL,
                                                                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                      updated_by VARCHAR(100) NOT NULL,
                                                                      deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_security_management.security_sensitive_discovery IS '敏感数据发现结果表';

COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.id IS '自增ID';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.column_id IS '字段ID';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.rule_id IS '规则ID';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.sensitivity_level_id IS '敏感等级ID';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.confidence IS '置信度（1~100）';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.discovery_time IS '发现时间';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.created_at IS '创建时间';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.created_by IS '创建人';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.updated_at IS '更新时间';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.updated_by IS '更新人';
COMMENT ON COLUMN dam_security_management.security_sensitive_discovery.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_security_sensitive_discovery_column ON dam_security_management.security_sensitive_discovery(column_id);
--3. 数据访问申请表（security_access_application）
DROP TABLE IF EXISTS dam_security_management.security_access_application;

CREATE TABLE dam_security_management.security_access_application (
                                                                     id BIGSERIAL PRIMARY KEY,
                                                                     application_no VARCHAR(30) NOT NULL,
                                                                     applicant_id VARCHAR(64) NOT NULL,
                                                                     asset_id INTEGER NOT NULL,
                                                                     access_type VARCHAR(50) NOT NULL,
                                                                     access_reason TEXT NOT NULL,
                                                                     validity_days VARCHAR(50) NOT NULL,
                                                                     approver_id VARCHAR(64) NOT NULL,
                                                                     approve_comment TEXT,
                                                                     approved_at TIMESTAMP,
                                                                     effective_from TIMESTAMP NOT NULL,
                                                                     effective_to TIMESTAMP,
                                                                     status SMALLINT NOT NULL DEFAULT 1,
                                                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                     created_by VARCHAR(100) NOT NULL,
                                                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                     updated_by VARCHAR(100) NOT NULL,
                                                                     deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_security_management.security_access_application IS '数据访问申请表';

COMMENT ON COLUMN dam_security_management.security_access_application.id IS '自增ID';
COMMENT ON COLUMN dam_security_management.security_access_application.application_no IS '申请单号';
COMMENT ON COLUMN dam_security_management.security_access_application.applicant_id IS '申请人ID';
COMMENT ON COLUMN dam_security_management.security_access_application.asset_id IS '资产ID';
COMMENT ON COLUMN dam_security_management.security_access_application.access_type IS '访问类型';
COMMENT ON COLUMN dam_security_management.security_access_application.access_reason IS '申请理由';
COMMENT ON COLUMN dam_security_management.security_access_application.validity_days IS '有效期';
COMMENT ON COLUMN dam_security_management.security_access_application.approver_id IS '审批人ID';
COMMENT ON COLUMN dam_security_management.security_access_application.approve_comment IS '审批意见';
COMMENT ON COLUMN dam_security_management.security_access_application.approved_at IS '审批时间';
COMMENT ON COLUMN dam_security_management.security_access_application.effective_from IS '生效时间';
COMMENT ON COLUMN dam_security_management.security_access_application.effective_to IS '失效时间';
COMMENT ON COLUMN dam_security_management.security_access_application.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_security_management.security_access_application.created_at IS '创建时间';
COMMENT ON COLUMN dam_security_management.security_access_application.created_by IS '创建人';
COMMENT ON COLUMN dam_security_management.security_access_application.updated_at IS '更新时间';
COMMENT ON COLUMN dam_security_management.security_access_application.updated_by IS '更新人';
COMMENT ON COLUMN dam_security_management.security_access_application.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_security_access_application_no ON dam_security_management.security_access_application(application_no) WHERE deleted_at IS NULL;
--4. 数据操作审计表（security_data_audit_log）
DROP TABLE IF EXISTS dam_security_management.security_data_audit_log;

CREATE TABLE dam_security_management.security_data_audit_log (
                                                                 id BIGSERIAL PRIMARY KEY,
                                                                 trace_id VARCHAR(50) NOT NULL,
                                                                 session_id VARCHAR(64) NOT NULL,
                                                                 operator_id VARCHAR(50) NOT NULL,
                                                                 operator_ip VARCHAR(50) NOT NULL,
                                                                 asset_id BIGINT NOT NULL,
                                                                 asset_name VARCHAR(200),
                                                                 table_id BIGINT,
                                                                 table_name VARCHAR(200),
                                                                 operation_type VARCHAR(50) NOT NULL,
                                                                 sql_content TEXT,
                                                                 filter_condition TEXT,
                                                                 affected_rows BIGINT,
                                                                 data_size BIGINT,
                                                                 sensitivity_level VARCHAR(50),
                                                                 masking_applied BOOLEAN,
                                                                 permission_code VARCHAR(50),
                                                                 access_reason VARCHAR(200),
                                                                 is_approved BOOLEAN,
                                                                 risk_level VARCHAR(20),
                                                                 operation_time TIMESTAMP NOT NULL,
                                                                 created_at TIMESTAMP NOT NULL
);

COMMENT ON TABLE dam_security_management.security_data_audit_log IS '数据操作审计表';

COMMENT ON COLUMN dam_security_management.security_data_audit_log.id IS '自增ID';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.trace_id IS '链路追踪ID';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.session_id IS '会话ID';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.operator_id IS '操作人ID';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.operator_ip IS '操作人IP地址';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.asset_id IS '资产ID';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.asset_name IS '资产名称';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.table_id IS '表ID';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.table_name IS '表名';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.operation_type IS '操作类型：QUERY-查询，EXPORT-导出，DOWNLOAD-下载，COPY-复制，ANALYZE-分析';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.sql_content IS '执行的SQL语句';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.filter_condition IS '过滤/查询条件';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.affected_rows IS '影响/返回行数';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.data_size IS '返回数据量大小（字节）';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.sensitivity_level IS '最高敏感等级';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.masking_applied IS '是否应用了脱敏策略';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.permission_code IS '使用的权限标识';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.access_reason IS '访问原因';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.is_approved IS '是否为授权访问';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.risk_level IS '风险等级：LOW-低，MEDIUM-中，HIGH-高，CRITICAL-严重';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.operation_time IS '操作时间';
COMMENT ON COLUMN dam_security_management.security_data_audit_log.created_at IS '创建时间';

CREATE INDEX idx_security_data_audit_operator ON dam_security_management.security_data_audit_log(operator_id, operation_time DESC);
CREATE INDEX idx_security_data_audit_asset ON dam_security_management.security_data_audit_log(asset_id, operation_time DESC);
CREATE INDEX idx_security_data_audit_time ON dam_security_management.security_data_audit_log(operation_time DESC);
CREATE INDEX idx_security_data_audit_trace ON dam_security_management.security_data_audit_log(trace_id);
--5. 脱敏策略表（security_masking_policy）
--sql
DROP TABLE IF EXISTS dam_security_management.security_masking_policy;

CREATE TABLE dam_security_management.security_masking_policy (
                                                                 id BIGSERIAL PRIMARY KEY,
                                                                 policy_code VARCHAR(50) NOT NULL,
                                                                 policy_name VARCHAR(100) NOT NULL,
                                                                 target_type VARCHAR(50) NOT NULL,
                                                                 target_id BIGINT NOT NULL,
                                                                 target_column VARCHAR(100),
                                                                 masking_type VARCHAR(50) NOT NULL,
                                                                 masking_config JSONB NOT NULL,
                                                                 priority INTEGER NOT NULL,
                                                                 apply_scenario VARCHAR(50) NOT NULL,
                                                                 description VARCHAR(500),
                                                                 status SMALLINT NOT NULL DEFAULT 1,
                                                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                 created_by VARCHAR(100) NOT NULL,
                                                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                 updated_by VARCHAR(100) NOT NULL,
                                                                 deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_security_management.security_masking_policy IS '脱敏策略表';

COMMENT ON COLUMN dam_security_management.security_masking_policy.id IS '自增ID';
COMMENT ON COLUMN dam_security_management.security_masking_policy.policy_code IS '策略编码';
COMMENT ON COLUMN dam_security_management.security_masking_policy.policy_name IS '策略名称';
COMMENT ON COLUMN dam_security_management.security_masking_policy.target_type IS '目标类型：COLUMN-字段，TABLE-表，SCHEMA-模式';
COMMENT ON COLUMN dam_security_management.security_masking_policy.target_id IS '目标ID';
COMMENT ON COLUMN dam_security_management.security_masking_policy.target_column IS '目标字段名（字段级脱敏时填写）';
COMMENT ON COLUMN dam_security_management.security_masking_policy.masking_type IS '脱敏类型：MASK-掩码，REPLACE-替换，ENCRYPT-加密，HASH-哈希，NULL-置空';
COMMENT ON COLUMN dam_security_management.security_masking_policy.masking_config IS '脱敏配置（如掩码规则）';
COMMENT ON COLUMN dam_security_management.security_masking_policy.priority IS '优先级（数值越大优先级越高）';
COMMENT ON COLUMN dam_security_management.security_masking_policy.apply_scenario IS '应用场景：ALL-全部，QUERY-查询，EXPORT-导出，API-接口';
COMMENT ON COLUMN dam_security_management.security_masking_policy.description IS '策略描述';
COMMENT ON COLUMN dam_security_management.security_masking_policy.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_security_management.security_masking_policy.created_at IS '创建时间';
COMMENT ON COLUMN dam_security_management.security_masking_policy.created_by IS '创建人';
COMMENT ON COLUMN dam_security_management.security_masking_policy.updated_at IS '更新时间';
COMMENT ON COLUMN dam_security_management.security_masking_policy.updated_by IS '更新人';
COMMENT ON COLUMN dam_security_management.security_masking_policy.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_security_masking_policy_code ON dam_security_management.security_masking_policy(policy_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_security_masking_policy_target ON dam_security_management.security_masking_policy(target_type, target_id);

--6. ABAC数据权限表（security_data_permission）

DROP TABLE IF EXISTS dam_security_management.security_data_permission;

CREATE TABLE dam_security_management.security_data_permission (
                                                                  id BIGSERIAL PRIMARY KEY,
                                                                  permission_code VARCHAR(50) NOT NULL,
                                                                  permission_name VARCHAR(100) NOT NULL,
                                                                  resource_type VARCHAR(50) NOT NULL,
                                                                  resource_id BIGINT NOT NULL,
                                                                  subject_type VARCHAR(50) NOT NULL,
                                                                  subject_id VARCHAR(100) NOT NULL,
                                                                  action_type VARCHAR(100) NOT NULL,
                                                                  filter_condition TEXT,
                                                                  column_mask TEXT[],
                                                                  priority INTEGER NOT NULL,
                                                                  effective_from TIMESTAMP NOT NULL,
                                                                  effective_to TIMESTAMP,
                                                                  description VARCHAR(500),
                                                                  status SMALLINT NOT NULL DEFAULT 1,
                                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                  created_by VARCHAR(100) NOT NULL,
                                                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                                  updated_by VARCHAR(100) NOT NULL,
                                                                  deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_security_management.security_data_permission IS 'ABAC数据权限表';

COMMENT ON COLUMN dam_security_management.security_data_permission.id IS '自增ID';
COMMENT ON COLUMN dam_security_management.security_data_permission.permission_code IS '权限编码';
COMMENT ON COLUMN dam_security_management.security_data_permission.permission_name IS '权限名称';
COMMENT ON COLUMN dam_security_management.security_data_permission.resource_type IS '资源类型：TABLE-表，COLUMN-字段，DATABASE-数据库，SCHEMA-模式';
COMMENT ON COLUMN dam_security_management.security_data_permission.resource_id IS '资源ID';
COMMENT ON COLUMN dam_security_management.security_data_permission.subject_type IS '主体类型：USER-用户，ROLE-角色，GROUP-用户组，DEPT-部门';
COMMENT ON COLUMN dam_security_management.security_data_permission.subject_id IS '主体ID';
COMMENT ON COLUMN dam_security_management.security_data_permission.action_type IS '操作类型：SELECT-查询，INSERT-插入，UPDATE-更新，DELETE-删除，EXPORT-导出';
COMMENT ON COLUMN dam_security_management.security_data_permission.filter_condition IS '行级过滤条件';
COMMENT ON COLUMN dam_security_management.security_data_permission.column_mask IS '列级掩码（允许访问的字段列表）';
COMMENT ON COLUMN dam_security_management.security_data_permission.priority IS '优先级（数值越大优先级越高）';
COMMENT ON COLUMN dam_security_management.security_data_permission.effective_from IS '生效起始时间';
COMMENT ON COLUMN dam_security_management.security_data_permission.effective_to IS '生效结束时间';
COMMENT ON COLUMN dam_security_management.security_data_permission.description IS '权限描述';
COMMENT ON COLUMN dam_security_management.security_data_permission.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_security_management.security_data_permission.created_at IS '创建时间';
COMMENT ON COLUMN dam_security_management.security_data_permission.created_by IS '创建人';
COMMENT ON COLUMN dam_security_management.security_data_permission.updated_at IS '更新时间';
COMMENT ON COLUMN dam_security_management.security_data_permission.updated_by IS '更新人';
COMMENT ON COLUMN dam_security_management.security_data_permission.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_security_data_permission_code ON dam_security_management.security_data_permission(permission_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_security_data_permission_resource ON dam_security_management.security_data_permission(resource_type, resource_id);
CREATE INDEX idx_security_data_permission_subject ON dam_security_management.security_data_permission(subject_type, subject_id);


--六、数据标准模块（dam_standard）
--1. 标准码表（standard_code_table）
--sql
DROP TABLE IF EXISTS dam_standard.standard_code_table;

CREATE TABLE dam_standard.standard_code_table (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  code_table_code VARCHAR(100) NOT NULL,
                                                  code_table_name VARCHAR(100),
                                                  code_table_type VARCHAR(100) NOT NULL,
                                                  description TEXT,
                                                  status SMALLINT NOT NULL DEFAULT 1,
                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  created_by VARCHAR(100) NOT NULL,
                                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  updated_by VARCHAR(100) NOT NULL,
                                                  deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_standard.standard_code_table IS '标准码表';

COMMENT ON COLUMN dam_standard.standard_code_table.id IS '自增ID';
COMMENT ON COLUMN dam_standard.standard_code_table.code_table_code IS '码表代码';
COMMENT ON COLUMN dam_standard.standard_code_table.code_table_name IS '码表名称';
COMMENT ON COLUMN dam_standard.standard_code_table.code_table_type IS '码表类型';
COMMENT ON COLUMN dam_standard.standard_code_table.description IS '描述';
COMMENT ON COLUMN dam_standard.standard_code_table.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_standard.standard_code_table.created_at IS '创建时间';
COMMENT ON COLUMN dam_standard.standard_code_table.created_by IS '创建人';
COMMENT ON COLUMN dam_standard.standard_code_table.updated_at IS '更新时间';
COMMENT ON COLUMN dam_standard.standard_code_table.updated_by IS '更新人';
COMMENT ON COLUMN dam_standard.standard_code_table.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_standard_code_table_code ON dam_standard.standard_code_table(code_table_code) WHERE deleted_at IS NULL;
--2. 标准码值表（standard_code_value）
--sql
DROP TABLE IF EXISTS dam_standard.standard_code_value;

CREATE TABLE dam_standard.standard_code_value (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  code_table_id INTEGER,
                                                  code_value VARCHAR(200),
                                                  code_name VARCHAR(100),
                                                  parent_code_id INTEGER,
                                                  sort_order INTEGER,
                                                  description TEXT,
                                                  status SMALLINT NOT NULL DEFAULT 1,
                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  created_by VARCHAR(100) NOT NULL,
                                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  updated_by VARCHAR(100) NOT NULL,
                                                  deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_standard.standard_code_value IS '标准码值表';

COMMENT ON COLUMN dam_standard.standard_code_value.id IS '自增ID';
COMMENT ON COLUMN dam_standard.standard_code_value.code_table_id IS '所属码表ID';
COMMENT ON COLUMN dam_standard.standard_code_value.code_value IS '码值';
COMMENT ON COLUMN dam_standard.standard_code_value.code_name IS '码值名称';
COMMENT ON COLUMN dam_standard.standard_code_value.parent_code_id IS '父码值ID';
COMMENT ON COLUMN dam_standard.standard_code_value.sort_order IS '排序序号';
COMMENT ON COLUMN dam_standard.standard_code_value.description IS '描述';
COMMENT ON COLUMN dam_standard.standard_code_value.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_standard.standard_code_value.created_at IS '创建时间';
COMMENT ON COLUMN dam_standard.standard_code_value.created_by IS '创建人';
COMMENT ON COLUMN dam_standard.standard_code_value.updated_at IS '更新时间';
COMMENT ON COLUMN dam_standard.standard_code_value.updated_by IS '更新人';
COMMENT ON COLUMN dam_standard.standard_code_value.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_standard_code_value_table ON dam_standard.standard_code_value(code_table_id);
--3. 标准映射表（standard_mapping）
--sql
DROP TABLE IF EXISTS dam_standard.standard_mapping;

CREATE TABLE dam_standard.standard_mapping (
                                               id BIGSERIAL PRIMARY KEY,
                                               source_system_id INTEGER NOT NULL,
                                               source_table_id INTEGER NOT NULL,
                                               source_column VARCHAR(200) NOT NULL,
                                               source_value VARCHAR(200) NOT NULL,
                                               standard_code_table_id INTEGER NOT NULL,
                                               standard_code_value VARCHAR(200) NOT NULL,
                                               mapping_rule TEXT NOT NULL,
                                               status SMALLINT NOT NULL DEFAULT 1,
                                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               created_by VARCHAR(100) NOT NULL,
                                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               updated_by VARCHAR(100) NOT NULL,
                                               deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_standard.standard_mapping IS '标准映射表';

COMMENT ON COLUMN dam_standard.standard_mapping.id IS '自增ID';
COMMENT ON COLUMN dam_standard.standard_mapping.source_system_id IS '源系统ID';
COMMENT ON COLUMN dam_standard.standard_mapping.source_table_id IS '源表ID';
COMMENT ON COLUMN dam_standard.standard_mapping.source_column IS '源字段';
COMMENT ON COLUMN dam_standard.standard_mapping.source_value IS '源码值';
COMMENT ON COLUMN dam_standard.standard_mapping.standard_code_table_id IS '标准码表ID';
COMMENT ON COLUMN dam_standard.standard_mapping.standard_code_value IS '标准码值';
COMMENT ON COLUMN dam_standard.standard_mapping.mapping_rule IS '映射规则';
COMMENT ON COLUMN dam_standard.standard_mapping.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_standard.standard_mapping.created_at IS '创建时间';
COMMENT ON COLUMN dam_standard.standard_mapping.created_by IS '创建人';
COMMENT ON COLUMN dam_standard.standard_mapping.updated_at IS '更新时间';
COMMENT ON COLUMN dam_standard.standard_mapping.updated_by IS '更新人';
COMMENT ON COLUMN dam_standard.standard_mapping.deleted_at IS '删除时间（软删除）';
--4. 标准偏差报告表（standard_deviation_report）
--sql
DROP TABLE IF EXISTS dam_standard.standard_deviation_report;

CREATE TABLE dam_standard.standard_deviation_report (
                                                        id BIGSERIAL PRIMARY KEY,
                                                        report_no VARCHAR(50) NOT NULL,
                                                        check_type VARCHAR(30) NOT NULL,
                                                        table_id BIGINT NOT NULL,
                                                        column_name VARCHAR(200),
                                                        standard_code_table_id BIGINT NOT NULL,
                                                        expected_value VARCHAR(200) NOT NULL,
                                                        actual_value VARCHAR(200) NOT NULL,
                                                        deviation_description TEXT,
                                                        severity VARCHAR(20) NOT NULL,
                                                        fix_suggestion TEXT,
                                                        fixed_by VARCHAR(100),
                                                        fixed_at TIMESTAMP,
                                                        check_time TIMESTAMP,
                                                        status SMALLINT NOT NULL DEFAULT 1,
                                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        created_by VARCHAR(100) NOT NULL,
                                                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        updated_by VARCHAR(100) NOT NULL,
                                                        deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_standard.standard_deviation_report IS '标准偏差报告表';

COMMENT ON COLUMN dam_standard.standard_deviation_report.id IS '自增ID';
COMMENT ON COLUMN dam_standard.standard_deviation_report.report_no IS '报告编号';
COMMENT ON COLUMN dam_standard.standard_deviation_report.check_type IS '检查类型：COLUMN_NAMING-字段命名，FIELD_TYPE-字段类型，VALUE_RANGE-值域，CODE_MAPPING-码值映射';
COMMENT ON COLUMN dam_standard.standard_deviation_report.table_id IS '关联表ID';
COMMENT ON COLUMN dam_standard.standard_deviation_report.column_name IS '偏差字段名';
COMMENT ON COLUMN dam_standard.standard_deviation_report.standard_code_table_id IS '关联标准码表ID';
COMMENT ON COLUMN dam_standard.standard_deviation_report.expected_value IS '期望值（符合标准的值）';
COMMENT ON COLUMN dam_standard.standard_deviation_report.actual_value IS '实际值（当前的值）';
COMMENT ON COLUMN dam_standard.standard_deviation_report.deviation_description IS '偏差描述';
COMMENT ON COLUMN dam_standard.standard_deviation_report.severity IS '严重程度：HIGH-高，MEDIUM-中，LOW-低';
COMMENT ON COLUMN dam_standard.standard_deviation_report.fix_suggestion IS '修复建议';
COMMENT ON COLUMN dam_standard.standard_deviation_report.fixed_by IS '修复人ID';
COMMENT ON COLUMN dam_standard.standard_deviation_report.fixed_at IS '修复时间';
COMMENT ON COLUMN dam_standard.standard_deviation_report.check_time IS '检查时间';
COMMENT ON COLUMN dam_standard.standard_deviation_report.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_standard.standard_deviation_report.created_at IS '创建时间';
COMMENT ON COLUMN dam_standard.standard_deviation_report.created_by IS '创建人';
COMMENT ON COLUMN dam_standard.standard_deviation_report.updated_at IS '更新时间';
COMMENT ON COLUMN dam_standard.standard_deviation_report.updated_by IS '更新人';
COMMENT ON COLUMN dam_standard.standard_deviation_report.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_standard_deviation_report_no ON dam_standard.standard_deviation_report(report_no) WHERE deleted_at IS NULL;
CREATE INDEX idx_standard_deviation_report_table ON dam_standard.standard_deviation_report(table_id);
--七、工单中心模块（dam_ticket_center）
--1. 工单主表（ticket_order）
DROP TABLE IF EXISTS dam_ticket_center.ticket_order;

CREATE TABLE dam_ticket_center.ticket_order (
                                                id BIGSERIAL PRIMARY KEY,
                                                ticket_no VARCHAR(50) NOT NULL,
                                                ticket_type VARCHAR(50) NOT NULL,
                                                title VARCHAR(100) NOT NULL,
                                                description TEXT,
                                                priority VARCHAR(100) NOT NULL,
                                                applicant_id INTEGER NOT NULL,
                                                assignee_id INTEGER,
                                                related_asset_id INTEGER,
                                                related_application_id INTEGER,
                                                expected_complete_time TIMESTAMP,
                                                actual_complete_time TIMESTAMP,
                                                status SMALLINT NOT NULL DEFAULT 1,
                                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                created_by VARCHAR(100) NOT NULL,
                                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_by VARCHAR(100) NOT NULL,
                                                deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_ticket_center.ticket_order IS '工单主表';

COMMENT ON COLUMN dam_ticket_center.ticket_order.id IS '自增ID';
COMMENT ON COLUMN dam_ticket_center.ticket_order.ticket_no IS '工单编号';
COMMENT ON COLUMN dam_ticket_center.ticket_order.ticket_type IS '工单类型';
COMMENT ON COLUMN dam_ticket_center.ticket_order.title IS '工单标题';
COMMENT ON COLUMN dam_ticket_center.ticket_order.description IS '描述';
COMMENT ON COLUMN dam_ticket_center.ticket_order.priority IS '优先级：LOW-低，MEDIUM-中，HIGH-高，URGENT-紧急';
COMMENT ON COLUMN dam_ticket_center.ticket_order.applicant_id IS '申请人ID';
COMMENT ON COLUMN dam_ticket_center.ticket_order.assignee_id IS '处理人ID';
COMMENT ON COLUMN dam_ticket_center.ticket_order.related_asset_id IS '关联资产ID';
COMMENT ON COLUMN dam_ticket_center.ticket_order.related_application_id IS '关联申请单ID';
COMMENT ON COLUMN dam_ticket_center.ticket_order.expected_complete_time IS '期望完成时间';
COMMENT ON COLUMN dam_ticket_center.ticket_order.actual_complete_time IS '实际完成时间';
COMMENT ON COLUMN dam_ticket_center.ticket_order.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_ticket_center.ticket_order.created_at IS '创建时间';
COMMENT ON COLUMN dam_ticket_center.ticket_order.created_by IS '创建人';
COMMENT ON COLUMN dam_ticket_center.ticket_order.updated_at IS '更新时间';
COMMENT ON COLUMN dam_ticket_center.ticket_order.updated_by IS '更新人';
COMMENT ON COLUMN dam_ticket_center.ticket_order.deleted_at IS '删除时间（软删除）';

CREATE UNIQUE INDEX idx_ticket_order_no ON dam_ticket_center.ticket_order(ticket_no) WHERE deleted_at IS NULL;
--2. 工单状态流转记录表（ticket_flow_log）
DROP TABLE IF EXISTS dam_ticket_center.ticket_flow_log;

CREATE TABLE dam_ticket_center.ticket_flow_log (
                                                   id BIGSERIAL PRIMARY KEY,
                                                   ticket_id INTEGER NOT NULL,
                                                   from_status VARCHAR(50) NOT NULL,
                                                   to_status VARCHAR(50) NOT NULL,
                                                   operator_id INTEGER NOT NULL,
                                                   operation_type VARCHAR(50) NOT NULL,
                                                   comment VARCHAR(500),
                                                   attachments JSONB,
                                                   status SMALLINT NOT NULL DEFAULT 1,
                                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                   created_by VARCHAR(100) NOT NULL,
                                                   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                   updated_by VARCHAR(100) NOT NULL,
                                                   deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_ticket_center.ticket_flow_log IS '工单状态流转记录表';

COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.id IS '自增ID';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.ticket_id IS '工单ID';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.from_status IS '变更前状态';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.to_status IS '变更后状态';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.operator_id IS '操作人ID';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.operation_type IS '操作类型：SUBMIT-提交，ASSIGN-指派，PROCESS-处理，REVIEW-复核，COMPLETE-完成，CLOSE-关闭，REJECT-驳回';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.comment IS '备注';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.attachments IS '附件';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.created_at IS '创建时间';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.created_by IS '创建人';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.updated_at IS '更新时间';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.updated_by IS '更新人';
COMMENT ON COLUMN dam_ticket_center.ticket_flow_log.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_ticket_flow_log_ticket ON dam_ticket_center.ticket_flow_log(ticket_id);
--3. 资产使用统计表（asset_statistics）
DROP TABLE IF EXISTS dam_ticket_center.asset_statistics;

CREATE TABLE dam_ticket_center.asset_statistics (
                                                    id BIGSERIAL PRIMARY KEY,
                                                    asset_id INTEGER NOT NULL,
                                                    stat_date TIMESTAMP NOT NULL,
                                                    view_count INTEGER NOT NULL,
                                                    query_count INTEGER NOT NULL,
                                                    download_count INTEGER NOT NULL,
                                                    export_count INTEGER NOT NULL,
                                                    unique_users INTEGER NOT NULL,
                                                    status SMALLINT NOT NULL DEFAULT 1,
                                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    created_by VARCHAR(100) NOT NULL,
                                                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    updated_by VARCHAR(100) NOT NULL,
                                                    deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_ticket_center.asset_statistics IS '资产使用统计表';

COMMENT ON COLUMN dam_ticket_center.asset_statistics.id IS '自增ID';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.asset_id IS '资产ID';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.stat_date IS '统计时间';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.view_count IS '浏览次数';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.query_count IS '查询次数';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.download_count IS '下载次数';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.export_count IS '导出次数';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.unique_users IS '独立用户数';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.created_at IS '创建时间';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.created_by IS '创建人';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.updated_at IS '更新时间';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.updated_by IS '更新人';
COMMENT ON COLUMN dam_ticket_center.asset_statistics.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_asset_statistics_asset ON dam_ticket_center.asset_statistics(asset_id);
CREATE INDEX idx_asset_statistics_date ON dam_ticket_center.asset_statistics(stat_date);
--4. 工单评论表（ticket_comment）
DROP TABLE IF EXISTS dam_ticket_center.ticket_comment;

CREATE TABLE dam_ticket_center.ticket_comment (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  ticket_id BIGINT NOT NULL,
                                                  parent_comment_id BIGINT,
                                                  content TEXT NOT NULL,
                                                  attachments JSONB,
                                                  mentioned_users TEXT[],
                                                  is_internal BOOLEAN,
                                                  commenter_id VARCHAR(100) NOT NULL,
                                                  status SMALLINT NOT NULL DEFAULT 1,
                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  created_by VARCHAR(100) NOT NULL,
                                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  updated_by VARCHAR(100) NOT NULL,
                                                  deleted_at TIMESTAMP
);

COMMENT ON TABLE dam_ticket_center.ticket_comment IS '工单评论表';

COMMENT ON COLUMN dam_ticket_center.ticket_comment.id IS '自增ID';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.ticket_id IS '工单ID';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.parent_comment_id IS '父评论ID';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.content IS '评论内容';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.attachments IS '附件列表';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.mentioned_users IS '@提及的用户ID列表';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.is_internal IS '是否内部评论（仅系统管理员可见）';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.commenter_id IS '评论人ID';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.created_at IS '创建时间';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.created_by IS '创建人';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.updated_at IS '更新时间';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.updated_by IS '更新人';
COMMENT ON COLUMN dam_ticket_center.ticket_comment.deleted_at IS '删除时间（软删除）';

CREATE INDEX idx_ticket_comment_ticket ON dam_ticket_center.ticket_comment(ticket_id);
CREATE INDEX idx_ticket_comment_commenter ON dam_ticket_center.ticket_comment(commenter_id);