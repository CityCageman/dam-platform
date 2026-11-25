--
-- PostgreSQL database dump
--

-- Dumped from database version 16.2
-- Dumped by pg_dump version 16.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: dam_dgp; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dgp;


--
-- Name: dam_dpm; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dpm;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: code_crosswalk; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.code_crosswalk (
    id bigint NOT NULL,
    code_table_id bigint NOT NULL,
    code_table_code character varying(64) NOT NULL,
    code_table_name character varying(128),
    source_system character varying(64) NOT NULL,
    source_table character varying(128),
    source_column character varying(128),
    source_value character varying(64) NOT NULL,
    source_name character varying(128),
    target_value character varying(64) NOT NULL,
    target_name character varying(128),
    priority integer DEFAULT 100 NOT NULL,
    remark text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE code_crosswalk; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.code_crosswalk IS '代码对照：源系统码值 → 平台标准码值（治理域持有，中台镜像执行）';


--
-- Name: COLUMN code_crosswalk.source_system; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.code_crosswalk.source_system IS '来源系统标识，与数据源登记的系统代号一致，如 HIS / LIS / PACS';


--
-- Name: COLUMN code_crosswalk.source_table; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.code_crosswalk.source_table IS '源表名（可选）：填写后中台可扫描该列实际出现的码值，自动发现未对照项';


--
-- Name: COLUMN code_crosswalk.source_column; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.code_crosswalk.source_column IS '源字段名（可选）：与 source_table 配对使用';


--
-- Name: COLUMN code_crosswalk.source_value; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.code_crosswalk.source_value IS '源系统实际存储的码值，按字符串比较（HIS 的 M/F、1/2/3/9 等）';


--
-- Name: COLUMN code_crosswalk.target_value; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.code_crosswalk.target_value IS '标准码值，必须是 standard_code_value 中该码表下存在的值';


--
-- Name: COLUMN code_crosswalk.priority; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.code_crosswalk.priority IS '同一源码值有多条对照时的取值优先级，数值小者优先';


--
-- Name: COLUMN code_crosswalk.remark; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.code_crosswalk.remark IS '生效依据/待确认说明';


--
-- Name: code_crosswalk_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.code_crosswalk_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: code_crosswalk_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.code_crosswalk_id_seq OWNED BY dam_dgp.code_crosswalk.id;


--
-- Name: data_standard; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.data_standard (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    category_id bigint,
    std_type character varying(32),
    rule_content text,
    example_value character varying(255),
    version character varying(16) DEFAULT 'v1.0'::character varying NOT NULL,
    owner character varying(100),
    effective_date date,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE data_standard; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.data_standard IS '数据标准库表—— 数据标准库（命名/编码/类型/枚举/度量规则标准，融合自现平台）';


--
-- Name: COLUMN data_standard.code; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.code IS '标准编码（唯一）';


--
-- Name: COLUMN data_standard.name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.name IS '标准名称';


--
-- Name: COLUMN data_standard.category_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.category_id IS '标准分类ID（关联 standard_category）';


--
-- Name: COLUMN data_standard.std_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.std_type IS '标准类型：NAMING命名/CODE编码/TYPE类型/ENUM枚举/METRIC度量';


--
-- Name: COLUMN data_standard.rule_content; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.rule_content IS '标准规则内容';


--
-- Name: COLUMN data_standard.example_value; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.example_value IS '示例值';


--
-- Name: COLUMN data_standard.version; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.version IS '标准版本';


--
-- Name: COLUMN data_standard.owner; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.owner IS '标准负责人';


--
-- Name: COLUMN data_standard.effective_date; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.effective_date IS '生效日期';


--
-- Name: COLUMN data_standard.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN data_standard.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.created_at IS '创建时间';


--
-- Name: COLUMN data_standard.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.created_by IS '创建人';


--
-- Name: COLUMN data_standard.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.updated_at IS '更新时间';


--
-- Name: COLUMN data_standard.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.updated_by IS '更新人';


--
-- Name: COLUMN data_standard.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.data_standard.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: data_standard_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.data_standard_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: data_standard_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.data_standard_id_seq OWNED BY dam_dgp.data_standard.id;


--
-- Name: security_access_application; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.security_access_application (
    id bigint NOT NULL,
    application_no character varying(30) NOT NULL,
    applicant_id character varying(64) NOT NULL,
    asset_id integer NOT NULL,
    access_type character varying(50) NOT NULL,
    access_reason text NOT NULL,
    validity_days character varying(50) NOT NULL,
    approver_id character varying(64) NOT NULL,
    approve_comment text,
    approved_at timestamp without time zone,
    effective_from timestamp without time zone NOT NULL,
    effective_to timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE security_access_application; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.security_access_application IS '数据访问申请表—— 数据访问申请（申请-审批流，RBAC+ABAC 的 ABAC 入口）';


--
-- Name: COLUMN security_access_application.application_no; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.application_no IS '申请单号';


--
-- Name: COLUMN security_access_application.applicant_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.applicant_id IS '申请人ID';


--
-- Name: COLUMN security_access_application.asset_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.asset_id IS '资产ID';


--
-- Name: COLUMN security_access_application.access_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.access_type IS '访问类型';


--
-- Name: COLUMN security_access_application.access_reason; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.access_reason IS '申请理由';


--
-- Name: COLUMN security_access_application.validity_days; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.validity_days IS '有效期';


--
-- Name: COLUMN security_access_application.approver_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.approver_id IS '审批人ID';


--
-- Name: COLUMN security_access_application.approve_comment; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.approve_comment IS '审批意见';


--
-- Name: COLUMN security_access_application.approved_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.approved_at IS '审批时间';


--
-- Name: COLUMN security_access_application.effective_from; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.effective_from IS '生效时间';


--
-- Name: COLUMN security_access_application.effective_to; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.effective_to IS '失效时间';


--
-- Name: COLUMN security_access_application.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.status IS '状态';


--
-- Name: COLUMN security_access_application.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.created_at IS '创建时间';


--
-- Name: COLUMN security_access_application.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.created_by IS '创建人';


--
-- Name: COLUMN security_access_application.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.updated_at IS '更新时间';


--
-- Name: COLUMN security_access_application.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.updated_by IS '更新人';


--
-- Name: COLUMN security_access_application.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_access_application.deleted_at IS '删除时间';


--
-- Name: security_access_application_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.security_access_application_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_access_application_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.security_access_application_id_seq OWNED BY dam_dgp.security_access_application.id;


--
-- Name: security_data_audit_log; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.security_data_audit_log (
    id bigint NOT NULL,
    trace_id character varying(50) NOT NULL,
    session_id character varying(64) NOT NULL,
    operator_id character varying(50) NOT NULL,
    operator_ip character varying(50) NOT NULL,
    asset_id bigint NOT NULL,
    asset_name character varying(200),
    table_id bigint,
    table_name character varying(200),
    operation_type character varying(50) NOT NULL,
    sql_content text,
    filter_condition text,
    affected_rows bigint,
    data_size bigint,
    sensitivity_level character varying(50),
    masking_applied boolean,
    permission_code character varying(50),
    access_reason character varying(200),
    is_approved boolean,
    risk_level character varying(20),
    operation_time timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE security_data_audit_log; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.security_data_audit_log IS '数据操作审计表—— 数据操作审计（访问/导出/脱敏命中）';


--
-- Name: COLUMN security_data_audit_log.trace_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.trace_id IS '链路追踪ID';


--
-- Name: COLUMN security_data_audit_log.session_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.session_id IS '会话ID';


--
-- Name: COLUMN security_data_audit_log.operator_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.operator_id IS '操作人ID';


--
-- Name: COLUMN security_data_audit_log.operator_ip; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.operator_ip IS '操作人IP地址';


--
-- Name: COLUMN security_data_audit_log.asset_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.asset_id IS '资产ID';


--
-- Name: COLUMN security_data_audit_log.asset_name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.asset_name IS '资产名称';


--
-- Name: COLUMN security_data_audit_log.table_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.table_id IS '表ID';


--
-- Name: COLUMN security_data_audit_log.table_name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.table_name IS '表名';


--
-- Name: COLUMN security_data_audit_log.operation_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.operation_type IS '操作类型';


--
-- Name: COLUMN security_data_audit_log.sql_content; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.sql_content IS '执行的SQL语句';


--
-- Name: COLUMN security_data_audit_log.filter_condition; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.filter_condition IS '过滤/查询条件';


--
-- Name: COLUMN security_data_audit_log.affected_rows; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.affected_rows IS '影响/返回行数';


--
-- Name: COLUMN security_data_audit_log.data_size; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.data_size IS '返回数据量大小（字节）';


--
-- Name: COLUMN security_data_audit_log.sensitivity_level; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.sensitivity_level IS '最高敏感等级';


--
-- Name: COLUMN security_data_audit_log.masking_applied; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.masking_applied IS '是否应用了脱敏策略';


--
-- Name: COLUMN security_data_audit_log.permission_code; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.permission_code IS '使用的权限标识';


--
-- Name: COLUMN security_data_audit_log.access_reason; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.access_reason IS '访问原因';


--
-- Name: COLUMN security_data_audit_log.is_approved; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.is_approved IS '是否为授权访问';


--
-- Name: COLUMN security_data_audit_log.risk_level; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.risk_level IS '风险等级';


--
-- Name: COLUMN security_data_audit_log.operation_time; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.operation_time IS '操作时间';


--
-- Name: COLUMN security_data_audit_log.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.created_at IS '创建时间';


--
-- Name: COLUMN security_data_audit_log.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN security_data_audit_log.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.created_by IS '创建人';


--
-- Name: COLUMN security_data_audit_log.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.updated_at IS '更新时间';


--
-- Name: COLUMN security_data_audit_log.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.updated_by IS '更新人';


--
-- Name: COLUMN security_data_audit_log.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_audit_log.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: security_data_audit_log_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.security_data_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_data_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.security_data_audit_log_id_seq OWNED BY dam_dgp.security_data_audit_log.id;


--
-- Name: security_data_permission; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.security_data_permission (
    id bigint NOT NULL,
    permission_code character varying(50) NOT NULL,
    permission_name character varying(100) NOT NULL,
    resource_type character varying(50) NOT NULL,
    resource_id bigint NOT NULL,
    subject_type character varying(50) NOT NULL,
    subject_id character varying(100) NOT NULL,
    action_type character varying(100) NOT NULL,
    filter_condition text,
    column_mask text[],
    priority integer NOT NULL,
    effective_from timestamp without time zone NOT NULL,
    effective_to timestamp without time zone,
    description character varying(500),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE security_data_permission; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.security_data_permission IS 'ABAC 数据权限表—— ABAC 数据权限（行级/列级规则）';


--
-- Name: COLUMN security_data_permission.permission_code; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.permission_code IS '权限编码';


--
-- Name: COLUMN security_data_permission.permission_name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.permission_name IS '权限名称';


--
-- Name: COLUMN security_data_permission.resource_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.resource_type IS '资源类型';


--
-- Name: COLUMN security_data_permission.resource_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.resource_id IS '资源ID';


--
-- Name: COLUMN security_data_permission.subject_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.subject_type IS '主体类型';


--
-- Name: COLUMN security_data_permission.subject_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.subject_id IS '主体ID';


--
-- Name: COLUMN security_data_permission.action_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.action_type IS '操作类型';


--
-- Name: COLUMN security_data_permission.filter_condition; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.filter_condition IS '行级过滤条件';


--
-- Name: COLUMN security_data_permission.column_mask; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.column_mask IS '列级掩码';


--
-- Name: COLUMN security_data_permission.priority; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.priority IS '优先级';


--
-- Name: COLUMN security_data_permission.effective_from; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.effective_from IS '生效时间';


--
-- Name: COLUMN security_data_permission.effective_to; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.effective_to IS '失效时间';


--
-- Name: COLUMN security_data_permission.description; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.description IS '描述';


--
-- Name: COLUMN security_data_permission.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.status IS '状态';


--
-- Name: COLUMN security_data_permission.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.created_at IS '创建时间';


--
-- Name: COLUMN security_data_permission.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.created_by IS '创建人';


--
-- Name: COLUMN security_data_permission.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.updated_at IS '更新时间';


--
-- Name: COLUMN security_data_permission.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.updated_by IS '更新人';


--
-- Name: COLUMN security_data_permission.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_data_permission.deleted_at IS '删除时间';


--
-- Name: security_data_permission_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.security_data_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_data_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.security_data_permission_id_seq OWNED BY dam_dgp.security_data_permission.id;


--
-- Name: security_masking_policy; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.security_masking_policy (
    id bigint NOT NULL,
    policy_code character varying(50) NOT NULL,
    policy_name character varying(100) NOT NULL,
    target_type character varying(50) NOT NULL,
    target_id bigint NOT NULL,
    target_column character varying(100),
    masking_type character varying(50) NOT NULL,
    masking_config jsonb NOT NULL,
    priority integer NOT NULL,
    apply_scenario character varying(50) NOT NULL,
    description character varying(500),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE security_masking_policy; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.security_masking_policy IS '脱敏策略表—— 脱敏策略（目标通用化：表/字段/资产/API 皆可挂；算法 MASK/PARTIAL/HASH/NULLIFY）';


--
-- Name: COLUMN security_masking_policy.policy_code; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.policy_code IS '策略编码';


--
-- Name: COLUMN security_masking_policy.policy_name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.policy_name IS '策略名称';


--
-- Name: COLUMN security_masking_policy.target_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.target_type IS '目标类型';


--
-- Name: COLUMN security_masking_policy.target_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.target_id IS '目标ID';


--
-- Name: COLUMN security_masking_policy.target_column; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.target_column IS '目标字段名';


--
-- Name: COLUMN security_masking_policy.masking_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.masking_type IS '脱敏类型';


--
-- Name: COLUMN security_masking_policy.masking_config; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.masking_config IS '脱敏配置';


--
-- Name: COLUMN security_masking_policy.priority; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.priority IS '优先级';


--
-- Name: COLUMN security_masking_policy.apply_scenario; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.apply_scenario IS '应用场景';


--
-- Name: COLUMN security_masking_policy.description; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.description IS '策略描述';


--
-- Name: COLUMN security_masking_policy.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.status IS '状态';


--
-- Name: COLUMN security_masking_policy.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.created_at IS '创建时间';


--
-- Name: COLUMN security_masking_policy.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.created_by IS '创建人';


--
-- Name: COLUMN security_masking_policy.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.updated_at IS '更新时间';


--
-- Name: COLUMN security_masking_policy.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.updated_by IS '更新人';


--
-- Name: COLUMN security_masking_policy.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_masking_policy.deleted_at IS '删除时间';


--
-- Name: security_masking_policy_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.security_masking_policy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_masking_policy_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.security_masking_policy_id_seq OWNED BY dam_dgp.security_masking_policy.id;


--
-- Name: security_sensitive_discovery; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.security_sensitive_discovery (
    id bigint NOT NULL,
    column_id integer NOT NULL,
    rule_id integer NOT NULL,
    sensitivity_level_id integer NOT NULL,
    confidence numeric(5,2),
    discovery_time timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE security_sensitive_discovery; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.security_sensitive_discovery IS '敏感数据发现结果表—— 敏感数据发现结果（扫描批次/命中列/置信度）';


--
-- Name: COLUMN security_sensitive_discovery.column_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.column_id IS '字段ID';


--
-- Name: COLUMN security_sensitive_discovery.rule_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.rule_id IS '规则ID';


--
-- Name: COLUMN security_sensitive_discovery.sensitivity_level_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.sensitivity_level_id IS '敏感等级ID';


--
-- Name: COLUMN security_sensitive_discovery.confidence; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.confidence IS '置信度（1~100）';


--
-- Name: COLUMN security_sensitive_discovery.discovery_time; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.discovery_time IS '发现时间';


--
-- Name: COLUMN security_sensitive_discovery.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.status IS '状态';


--
-- Name: COLUMN security_sensitive_discovery.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.created_at IS '创建时间';


--
-- Name: COLUMN security_sensitive_discovery.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.created_by IS '创建人';


--
-- Name: COLUMN security_sensitive_discovery.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.updated_at IS '更新时间';


--
-- Name: COLUMN security_sensitive_discovery.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.updated_by IS '更新人';


--
-- Name: COLUMN security_sensitive_discovery.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_discovery.deleted_at IS '删除时间';


--
-- Name: security_sensitive_discovery_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.security_sensitive_discovery_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_sensitive_discovery_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.security_sensitive_discovery_id_seq OWNED BY dam_dgp.security_sensitive_discovery.id;


--
-- Name: security_sensitive_rule; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.security_sensitive_rule (
    id bigint NOT NULL,
    rule_code character varying(100),
    rule_name character varying(200),
    sensitivity_level character varying(20),
    rule_type character varying(50),
    rule_pattern character varying(200),
    dictionary_id integer,
    description text,
    priority character varying(20),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE security_sensitive_rule; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.security_sensitive_rule IS '敏感数据规则表—— 敏感数据识别规则（正则/关键字/字典，身份证/手机号/银行卡等）';


--
-- Name: COLUMN security_sensitive_rule.rule_code; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.rule_code IS '规则代码';


--
-- Name: COLUMN security_sensitive_rule.rule_name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.rule_name IS '规则名称';


--
-- Name: COLUMN security_sensitive_rule.sensitivity_level; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.sensitivity_level IS '敏感登记';


--
-- Name: COLUMN security_sensitive_rule.rule_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.rule_type IS '规则类型';


--
-- Name: COLUMN security_sensitive_rule.rule_pattern; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.rule_pattern IS '匹配模式';


--
-- Name: COLUMN security_sensitive_rule.dictionary_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.dictionary_id IS '关联词典ID';


--
-- Name: COLUMN security_sensitive_rule.description; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.description IS '描述';


--
-- Name: COLUMN security_sensitive_rule.priority; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.priority IS '优先级';


--
-- Name: COLUMN security_sensitive_rule.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.status IS '状态';


--
-- Name: COLUMN security_sensitive_rule.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.created_at IS '创建时间';


--
-- Name: COLUMN security_sensitive_rule.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.created_by IS '创建人';


--
-- Name: COLUMN security_sensitive_rule.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.updated_at IS '更新时间';


--
-- Name: COLUMN security_sensitive_rule.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.updated_by IS '更新人';


--
-- Name: COLUMN security_sensitive_rule.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.security_sensitive_rule.deleted_at IS '删除时间';


--
-- Name: security_sensitive_rule_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.security_sensitive_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_sensitive_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.security_sensitive_rule_id_seq OWNED BY dam_dgp.security_sensitive_rule.id;


--
-- Name: standard_category; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.standard_category (
    id bigint NOT NULL,
    parent_id bigint,
    code character varying(64),
    name character varying(128) NOT NULL,
    description text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE standard_category; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.standard_category IS '标准分类表—— 标准分类（规则型标准树，融合自现平台）';


--
-- Name: COLUMN standard_category.parent_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.parent_id IS '父分类ID';


--
-- Name: COLUMN standard_category.code; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.code IS '分类编码';


--
-- Name: COLUMN standard_category.name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.name IS '分类名称';


--
-- Name: COLUMN standard_category.description; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.description IS '分类描述';


--
-- Name: COLUMN standard_category.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN standard_category.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.created_at IS '创建时间';


--
-- Name: COLUMN standard_category.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.created_by IS '创建人';


--
-- Name: COLUMN standard_category.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.updated_at IS '更新时间';


--
-- Name: COLUMN standard_category.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.updated_by IS '更新人';


--
-- Name: COLUMN standard_category.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_category.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: standard_category_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.standard_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: standard_category_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.standard_category_id_seq OWNED BY dam_dgp.standard_category.id;


--
-- Name: standard_code_table; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.standard_code_table (
    id bigint NOT NULL,
    code_table_code character varying(100) NOT NULL,
    code_table_name character varying(100),
    code_table_type character varying(100) NOT NULL,
    description text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone,
    standard_source character varying(16),
    standard_id character varying(64),
    standard_name character varying(255),
    standard_version character varying(32)
);


--
-- Name: TABLE standard_code_table; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.standard_code_table IS '标准码表—— 标准码表（DIM 14 类医疗字典的码表形态）';


--
-- Name: COLUMN standard_code_table.code_table_code; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.code_table_code IS '码表代码';


--
-- Name: COLUMN standard_code_table.code_table_name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.code_table_name IS '码表名称';


--
-- Name: COLUMN standard_code_table.code_table_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.code_table_type IS '码表类型';


--
-- Name: COLUMN standard_code_table.description; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.description IS '描述';


--
-- Name: COLUMN standard_code_table.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.status IS '状态';


--
-- Name: COLUMN standard_code_table.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.created_at IS '创建时间';


--
-- Name: COLUMN standard_code_table.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.created_by IS '创建人';


--
-- Name: COLUMN standard_code_table.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.updated_at IS '更新时间';


--
-- Name: COLUMN standard_code_table.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.updated_by IS '更新人';


--
-- Name: COLUMN standard_code_table.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.deleted_at IS '删除时间';


--
-- Name: COLUMN standard_code_table.standard_source; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.standard_source IS '标准层级：GB=国家标准 / WS=卫生行业标准 / NHC=卫健委数据字典 / LOCAL=院内扩展';


--
-- Name: COLUMN standard_code_table.standard_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.standard_id IS '标准中该码表的编号，如 GB/T 2261.1-2003、CV02.01.101、CT01.00.001';


--
-- Name: COLUMN standard_code_table.standard_name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.standard_name IS '标准全称';


--
-- Name: COLUMN standard_code_table.standard_version; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_table.standard_version IS '标准版本/发布日期';


--
-- Name: standard_code_table_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.standard_code_table_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: standard_code_table_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.standard_code_table_id_seq OWNED BY dam_dgp.standard_code_table.id;


--
-- Name: standard_code_value; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.standard_code_value (
    id bigint NOT NULL,
    code_table_id integer,
    code_value character varying(200),
    code_name character varying(100),
    parent_code_id integer,
    sort_order integer,
    description text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone,
    is_extension smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE standard_code_value; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.standard_code_value IS '标准码值表—— 标准码值（含 ICD-10/ICD-9-CM-3/科室/药品等）';


--
-- Name: COLUMN standard_code_value.code_table_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.code_table_id IS '所属码表ID';


--
-- Name: COLUMN standard_code_value.code_value; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.code_value IS '码值';


--
-- Name: COLUMN standard_code_value.code_name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.code_name IS '码值名称';


--
-- Name: COLUMN standard_code_value.parent_code_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.parent_code_id IS '父码值ID';


--
-- Name: COLUMN standard_code_value.sort_order; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.sort_order IS '排序序号';


--
-- Name: COLUMN standard_code_value.description; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.description IS '描述';


--
-- Name: COLUMN standard_code_value.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.status IS '状态';


--
-- Name: COLUMN standard_code_value.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.created_at IS '创建时间';


--
-- Name: COLUMN standard_code_value.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.created_by IS '创建人';


--
-- Name: COLUMN standard_code_value.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.updated_at IS '更新时间';


--
-- Name: COLUMN standard_code_value.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.updated_by IS '更新人';


--
-- Name: COLUMN standard_code_value.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.deleted_at IS '删除时间';


--
-- Name: COLUMN standard_code_value.is_extension; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_code_value.is_extension IS '1=标准中无此值的院内扩展，0=标准值';


--
-- Name: standard_code_value_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.standard_code_value_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: standard_code_value_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.standard_code_value_id_seq OWNED BY dam_dgp.standard_code_value.id;


--
-- Name: standard_deviation_report; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.standard_deviation_report (
    id bigint NOT NULL,
    report_no character varying(50) NOT NULL,
    check_type character varying(30) NOT NULL,
    table_id bigint NOT NULL,
    column_name character varying(200),
    standard_code_table_id bigint NOT NULL,
    expected_value character varying(200) NOT NULL,
    actual_value character varying(200) NOT NULL,
    deviation_description text,
    severity character varying(20) NOT NULL,
    fix_suggestion text,
    fixed_by character varying(100),
    fixed_at timestamp without time zone,
    check_time timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE standard_deviation_report; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.standard_deviation_report IS '标准偏差报告表—— 标准偏差报告（不符合项/整改跟踪）';


--
-- Name: COLUMN standard_deviation_report.report_no; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.report_no IS '报告编号';


--
-- Name: COLUMN standard_deviation_report.check_type; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.check_type IS '检查类型';


--
-- Name: COLUMN standard_deviation_report.table_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.table_id IS '关联表ID';


--
-- Name: COLUMN standard_deviation_report.column_name; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.column_name IS '偏差字段名';


--
-- Name: COLUMN standard_deviation_report.standard_code_table_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.standard_code_table_id IS '关联标准码表ID';


--
-- Name: COLUMN standard_deviation_report.expected_value; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.expected_value IS '期望值';


--
-- Name: COLUMN standard_deviation_report.actual_value; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.actual_value IS '实际值';


--
-- Name: COLUMN standard_deviation_report.deviation_description; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.deviation_description IS '偏差描述';


--
-- Name: COLUMN standard_deviation_report.severity; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.severity IS '严重程度';


--
-- Name: COLUMN standard_deviation_report.fix_suggestion; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.fix_suggestion IS '修复建议';


--
-- Name: COLUMN standard_deviation_report.fixed_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.fixed_by IS '修复人ID';


--
-- Name: COLUMN standard_deviation_report.fixed_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.fixed_at IS '修复时间';


--
-- Name: COLUMN standard_deviation_report.check_time; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.check_time IS '检查时间';


--
-- Name: COLUMN standard_deviation_report.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.status IS '状态';


--
-- Name: COLUMN standard_deviation_report.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.created_at IS '创建时间';


--
-- Name: COLUMN standard_deviation_report.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.created_by IS '创建人';


--
-- Name: COLUMN standard_deviation_report.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.updated_at IS '更新时间';


--
-- Name: COLUMN standard_deviation_report.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.updated_by IS '更新人';


--
-- Name: COLUMN standard_deviation_report.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_deviation_report.deleted_at IS '删除时间';


--
-- Name: standard_deviation_report_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.standard_deviation_report_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: standard_deviation_report_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.standard_deviation_report_id_seq OWNED BY dam_dgp.standard_deviation_report.id;


--
-- Name: standard_mapping; Type: TABLE; Schema: dam_dgp; Owner: -
--

CREATE TABLE dam_dgp.standard_mapping (
    id bigint NOT NULL,
    source_system_id integer NOT NULL,
    source_table_id integer NOT NULL,
    source_column character varying(200) NOT NULL,
    source_value character varying(200) NOT NULL,
    standard_code_table_id integer NOT NULL,
    standard_code_value character varying(200) NOT NULL,
    mapping_rule text NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE standard_mapping; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON TABLE dam_dgp.standard_mapping IS '标准映射表—— 标准落标映射（标准↔资产字段）';


--
-- Name: COLUMN standard_mapping.source_system_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.source_system_id IS '源系统ID';


--
-- Name: COLUMN standard_mapping.source_table_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.source_table_id IS '源表ID';


--
-- Name: COLUMN standard_mapping.source_column; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.source_column IS '源字段';


--
-- Name: COLUMN standard_mapping.source_value; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.source_value IS '源码值';


--
-- Name: COLUMN standard_mapping.standard_code_table_id; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.standard_code_table_id IS '标准码表ID';


--
-- Name: COLUMN standard_mapping.standard_code_value; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.standard_code_value IS '标准码值';


--
-- Name: COLUMN standard_mapping.mapping_rule; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.mapping_rule IS '映射规则';


--
-- Name: COLUMN standard_mapping.status; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.status IS '状态';


--
-- Name: COLUMN standard_mapping.created_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.created_at IS '创建时间';


--
-- Name: COLUMN standard_mapping.created_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.created_by IS '创建人';


--
-- Name: COLUMN standard_mapping.updated_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.updated_at IS '更新时间';


--
-- Name: COLUMN standard_mapping.updated_by; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.updated_by IS '更新人';


--
-- Name: COLUMN standard_mapping.deleted_at; Type: COMMENT; Schema: dam_dgp; Owner: -
--

COMMENT ON COLUMN dam_dgp.standard_mapping.deleted_at IS '删除时间';


--
-- Name: standard_mapping_id_seq; Type: SEQUENCE; Schema: dam_dgp; Owner: -
--

CREATE SEQUENCE dam_dgp.standard_mapping_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: standard_mapping_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dgp; Owner: -
--

ALTER SEQUENCE dam_dgp.standard_mapping_id_seq OWNED BY dam_dgp.standard_mapping.id;


--
-- Name: sched_job; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sched_job (
    id bigint NOT NULL,
    job_code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    job_type character varying(64) NOT NULL,
    schedule_type character varying(20) DEFAULT 'CRON'::character varying NOT NULL,
    cron_expr character varying(64),
    fixed_rate_ms bigint,
    powerjob_job_id bigint,
    enabled boolean DEFAULT true NOT NULL,
    last_run_at timestamp without time zone,
    last_status character varying(16),
    last_message text,
    run_count bigint DEFAULT 0 NOT NULL,
    description text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sched_job; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sched_job IS '调度任务定义表—— 调度任务配置层（新增 schedule_type/powerjob_job_id，执行触发由 PowerJob 承担）';


--
-- Name: COLUMN sched_job.job_code; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.job_code IS '任务编码（唯一）';


--
-- Name: COLUMN sched_job.name; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.name IS '任务名称';


--
-- Name: COLUMN sched_job.job_type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.job_type IS '任务类型：QUALITY_CHECK/METADATA_SYNC/LOG_CLEAN/CUSTOM';


--
-- Name: COLUMN sched_job.schedule_type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.schedule_type IS '调度类型：CRON/FIXED_RATE/API（新增）';


--
-- Name: COLUMN sched_job.cron_expr; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.cron_expr IS 'CRON 表达式（schedule_type=CRON 时必填）';


--
-- Name: COLUMN sched_job.fixed_rate_ms; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.fixed_rate_ms IS '固定频率毫秒数（schedule_type=FIXED_RATE 时使用，新增）';


--
-- Name: COLUMN sched_job.powerjob_job_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.powerjob_job_id IS 'PowerJob 侧任务ID（触发内核对接，新增）';


--
-- Name: COLUMN sched_job.enabled; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.enabled IS '是否启用';


--
-- Name: COLUMN sched_job.last_run_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.last_run_at IS '最近执行时间';


--
-- Name: COLUMN sched_job.last_status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.last_status IS '最近执行状态';


--
-- Name: COLUMN sched_job.last_message; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.last_message IS '最近执行消息';


--
-- Name: COLUMN sched_job.run_count; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.run_count IS '累计执行次数';


--
-- Name: COLUMN sched_job.description; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.description IS '任务说明';


--
-- Name: COLUMN sched_job.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN sched_job.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.created_at IS '创建时间';


--
-- Name: COLUMN sched_job.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.created_by IS '创建人';


--
-- Name: COLUMN sched_job.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.updated_at IS '更新时间';


--
-- Name: COLUMN sched_job.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.updated_by IS '更新人';


--
-- Name: COLUMN sched_job.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: sched_job_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sched_job_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sched_job_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sched_job_id_seq OWNED BY dam_dpm.sched_job.id;


--
-- Name: sched_job_log; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sched_job_log (
    id bigint NOT NULL,
    job_id bigint NOT NULL,
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    finished_at timestamp without time zone,
    duration_ms bigint,
    exec_status character varying(16),
    message text,
    powerjob_instance_id bigint,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sched_job_log; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sched_job_log IS '调度执行日志表—— 调度执行日志（新增 powerjob_instance_id 与 PowerJob 双端对账）';


--
-- Name: COLUMN sched_job_log.job_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.job_id IS '任务ID（关联 sched_job.id）';


--
-- Name: COLUMN sched_job_log.started_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.started_at IS '开始时间';


--
-- Name: COLUMN sched_job_log.finished_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.finished_at IS '结束时间';


--
-- Name: COLUMN sched_job_log.duration_ms; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.duration_ms IS '耗时（毫秒）';


--
-- Name: COLUMN sched_job_log.exec_status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.exec_status IS '执行状态：SUCCESS/FAILED/RUNNING（更名避免与审计 status 撞名）';


--
-- Name: COLUMN sched_job_log.message; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.message IS '执行消息/错误';


--
-- Name: COLUMN sched_job_log.powerjob_instance_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.powerjob_instance_id IS 'PowerJob 任务实例ID（新增，双端对账）';


--
-- Name: COLUMN sched_job_log.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN sched_job_log.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.created_at IS '创建时间';


--
-- Name: COLUMN sched_job_log.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.created_by IS '创建人';


--
-- Name: COLUMN sched_job_log.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.updated_at IS '更新时间';


--
-- Name: COLUMN sched_job_log.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.updated_by IS '更新人';


--
-- Name: COLUMN sched_job_log.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sched_job_log.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: sched_job_log_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sched_job_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sched_job_log_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sched_job_log_id_seq OWNED BY dam_dpm.sched_job_log.id;


--
-- Name: sys_audit_log; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_audit_log (
    id bigint NOT NULL,
    trace_id character varying(50) NOT NULL,
    session_id character varying(64) NOT NULL,
    operator_id character varying(64) NOT NULL,
    operator_ip character varying(45) NOT NULL,
    user_agent text,
    module character varying(50) NOT NULL,
    operation character varying(100),
    operation_type character varying(30),
    target_type character varying(30),
    target_id character varying(100),
    target_name character varying(200),
    request_method character varying(10),
    request_url character varying(500),
    request_params jsonb,
    response_status integer,
    response_body text,
    duration_ms integer,
    is_success boolean,
    error_message text,
    risk_level character varying(20),
    operation_time timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_audit_log; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_audit_log IS '系统操作审计表—— 系统操作审计（登录/增删改/授权等全量留痕）';


--
-- Name: COLUMN sys_audit_log.trace_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.trace_id IS '链路追踪ID';


--
-- Name: COLUMN sys_audit_log.session_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.session_id IS '会话ID';


--
-- Name: COLUMN sys_audit_log.operator_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.operator_id IS '操作人ID';


--
-- Name: COLUMN sys_audit_log.operator_ip; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.operator_ip IS '操作人IP地址';


--
-- Name: COLUMN sys_audit_log.user_agent; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.user_agent IS '客户端UA';


--
-- Name: COLUMN sys_audit_log.module; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.module IS '操作模块';


--
-- Name: COLUMN sys_audit_log.operation; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.operation IS '操作描述';


--
-- Name: COLUMN sys_audit_log.operation_type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.operation_type IS '操作类型';


--
-- Name: COLUMN sys_audit_log.target_type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.target_type IS '目标类型';


--
-- Name: COLUMN sys_audit_log.target_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.target_id IS '目标ID';


--
-- Name: COLUMN sys_audit_log.target_name; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.target_name IS '目标名称';


--
-- Name: COLUMN sys_audit_log.request_method; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.request_method IS '请求方法';


--
-- Name: COLUMN sys_audit_log.request_url; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.request_url IS '请求URL';


--
-- Name: COLUMN sys_audit_log.request_params; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.request_params IS '请求参数';


--
-- Name: COLUMN sys_audit_log.response_status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.response_status IS 'HTTP响应状态码';


--
-- Name: COLUMN sys_audit_log.response_body; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.response_body IS '响应体摘要';


--
-- Name: COLUMN sys_audit_log.duration_ms; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.duration_ms IS '响应耗时（毫秒）';


--
-- Name: COLUMN sys_audit_log.is_success; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.is_success IS '是否操作成功';


--
-- Name: COLUMN sys_audit_log.error_message; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.error_message IS '异常堆栈摘要';


--
-- Name: COLUMN sys_audit_log.risk_level; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.risk_level IS '风险等级';


--
-- Name: COLUMN sys_audit_log.operation_time; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.operation_time IS '操作时间';


--
-- Name: COLUMN sys_audit_log.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.created_at IS '创建时间';


--
-- Name: COLUMN sys_audit_log.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN sys_audit_log.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.created_by IS '创建人';


--
-- Name: COLUMN sys_audit_log.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.updated_at IS '更新时间';


--
-- Name: COLUMN sys_audit_log.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.updated_by IS '更新人';


--
-- Name: COLUMN sys_audit_log.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_audit_log.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: sys_audit_log_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_audit_log_id_seq OWNED BY dam_dpm.sys_audit_log.id;


--
-- Name: sys_config; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_config (
    id bigint NOT NULL,
    config_type character varying(50) NOT NULL,
    config_code character varying(50) NOT NULL,
    config_name character varying(100) NOT NULL,
    config_value text,
    description character varying(500),
    sort_order integer DEFAULT 0,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_config; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_config IS '系统配置表—— 系统参数配置（含数据源密码加密密钥等）';


--
-- Name: COLUMN sys_config.config_type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.config_type IS '配置分组：SYSTEM/NOTIFY/SECURITY/JOB';


--
-- Name: COLUMN sys_config.config_code; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.config_code IS '配置键';


--
-- Name: COLUMN sys_config.config_name; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.config_name IS '配置名称';


--
-- Name: COLUMN sys_config.config_value; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.config_value IS '配置值';


--
-- Name: COLUMN sys_config.description; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.description IS '配置说明';


--
-- Name: COLUMN sys_config.sort_order; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.sort_order IS '排序序号';


--
-- Name: COLUMN sys_config.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.status IS '状态';


--
-- Name: COLUMN sys_config.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.created_at IS '创建时间';


--
-- Name: COLUMN sys_config.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.created_by IS '创建人';


--
-- Name: COLUMN sys_config.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.updated_at IS '更新时间';


--
-- Name: COLUMN sys_config.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.updated_by IS '更新人';


--
-- Name: COLUMN sys_config.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_config.deleted_at IS '删除时间';


--
-- Name: sys_config_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_config_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_config_id_seq OWNED BY dam_dpm.sys_config.id;


--
-- Name: sys_dict_data; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_dict_data (
    id bigint NOT NULL,
    dict_type_id integer,
    dict_label character varying(100),
    dict_value character varying(200),
    dict_extra character varying(500),
    sort_order integer,
    is_default boolean,
    remarks character varying(500),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_dict_data; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_dict_data IS '字典数据表—— 系统级字典数据';


--
-- Name: COLUMN sys_dict_data.dict_type_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.dict_type_id IS '字典类型ID';


--
-- Name: COLUMN sys_dict_data.dict_label; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.dict_label IS '字典标签（界面展示名称，如：男、女）';


--
-- Name: COLUMN sys_dict_data.dict_value; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.dict_value IS '字典值（代码存储值，如：M、F）';


--
-- Name: COLUMN sys_dict_data.dict_extra; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.dict_extra IS '扩展值（如样式类名、颜色值等UI辅助信息）';


--
-- Name: COLUMN sys_dict_data.sort_order; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.sort_order IS '同级排序序号';


--
-- Name: COLUMN sys_dict_data.is_default; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.is_default IS '是否默认值';


--
-- Name: COLUMN sys_dict_data.remarks; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.remarks IS '备注';


--
-- Name: COLUMN sys_dict_data.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.status IS '状态';


--
-- Name: COLUMN sys_dict_data.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.created_at IS '创建时间';


--
-- Name: COLUMN sys_dict_data.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.created_by IS '创建人';


--
-- Name: COLUMN sys_dict_data.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.updated_at IS '更新时间';


--
-- Name: COLUMN sys_dict_data.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.updated_by IS '更新人';


--
-- Name: COLUMN sys_dict_data.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_data.deleted_at IS '删除时间';


--
-- Name: sys_dict_data_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_dict_data_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_dict_data_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_dict_data_id_seq OWNED BY dam_dpm.sys_dict_data.id;


--
-- Name: sys_dict_type; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_dict_type (
    id bigint NOT NULL,
    dict_type_code character varying(100),
    dict_type_name character varying(200),
    remark character varying(500),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_dict_type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_dict_type IS '通用字典类型表—— 系统级字典类型（状态/枚举类，业务码表勿放此处）';


--
-- Name: COLUMN sys_dict_type.dict_type_code; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_type.dict_type_code IS '字典类型编码';


--
-- Name: COLUMN sys_dict_type.dict_type_name; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_type.dict_type_name IS '字典类型名称';


--
-- Name: COLUMN sys_dict_type.remark; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_type.remark IS '备注';


--
-- Name: COLUMN sys_dict_type.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_type.status IS '状态';


--
-- Name: COLUMN sys_dict_type.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_type.created_at IS '创建时间';


--
-- Name: COLUMN sys_dict_type.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_type.created_by IS '创建人';


--
-- Name: COLUMN sys_dict_type.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_type.updated_at IS '更新时间';


--
-- Name: COLUMN sys_dict_type.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_type.updated_by IS '更新人';


--
-- Name: COLUMN sys_dict_type.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_dict_type.deleted_at IS '删除时间';


--
-- Name: sys_dict_type_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_dict_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_dict_type_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_dict_type_id_seq OWNED BY dam_dpm.sys_dict_type.id;


--
-- Name: sys_menu; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_menu (
    id bigint NOT NULL,
    menu_name character varying(50) NOT NULL,
    menu_type character varying(10) NOT NULL,
    parent_id bigint,
    tree_level integer DEFAULT 1,
    path character varying(200),
    component character varying(200),
    redirect character varying(200),
    icon character varying(50),
    sort_order integer DEFAULT 0,
    visible smallint DEFAULT 1 NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_menu; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_menu IS '系统菜单表—— 前端菜单树（配合角色控制可见性）';


--
-- Name: COLUMN sys_menu.menu_name; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.menu_name IS '菜单名称';


--
-- Name: COLUMN sys_menu.menu_type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.menu_type IS '类型M-目录，C-菜单，F-按钮';


--
-- Name: COLUMN sys_menu.parent_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.parent_id IS '父菜单ID';


--
-- Name: COLUMN sys_menu.tree_level; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.tree_level IS '树层级（根为1）';


--
-- Name: COLUMN sys_menu.path; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.path IS '路由地址';


--
-- Name: COLUMN sys_menu.component; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.component IS '组件路径';


--
-- Name: COLUMN sys_menu.redirect; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.redirect IS '重定向地址';


--
-- Name: COLUMN sys_menu.icon; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.icon IS '图标名称';


--
-- Name: COLUMN sys_menu.sort_order; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.sort_order IS '排序序号';


--
-- Name: COLUMN sys_menu.visible; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.visible IS '0不显示1显示';


--
-- Name: COLUMN sys_menu.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.status IS '状态';


--
-- Name: COLUMN sys_menu.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.created_at IS '创建时间';


--
-- Name: COLUMN sys_menu.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.created_by IS '创建人';


--
-- Name: COLUMN sys_menu.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.updated_at IS '更新时间';


--
-- Name: COLUMN sys_menu.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.updated_by IS '更新人';


--
-- Name: COLUMN sys_menu.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_menu.deleted_at IS '删除时间';


--
-- Name: sys_menu_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_menu_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_menu_id_seq OWNED BY dam_dpm.sys_menu.id;


--
-- Name: sys_permission; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_permission (
    id bigint NOT NULL,
    permission_code character varying(50) NOT NULL,
    permission_name character varying(100) NOT NULL,
    parent_id bigint,
    type character varying(20) NOT NULL,
    path character varying(200) NOT NULL,
    icon character varying(100),
    sort_order integer,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_permission; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_permission IS '权限表—— 权限点（接口级）定义';


--
-- Name: COLUMN sys_permission.permission_code; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.permission_code IS '权限编码';


--
-- Name: COLUMN sys_permission.permission_name; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.permission_name IS '权限名称';


--
-- Name: COLUMN sys_permission.parent_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.parent_id IS '父权限ID';


--
-- Name: COLUMN sys_permission.type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.type IS '类型: menu/button/api';


--
-- Name: COLUMN sys_permission.path; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.path IS '路由路径';


--
-- Name: COLUMN sys_permission.icon; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.icon IS '图标';


--
-- Name: COLUMN sys_permission.sort_order; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.sort_order IS '排序序号';


--
-- Name: COLUMN sys_permission.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.status IS '状态';


--
-- Name: COLUMN sys_permission.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.created_at IS '创建时间';


--
-- Name: COLUMN sys_permission.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.created_by IS '创建人';


--
-- Name: COLUMN sys_permission.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.updated_at IS '更新时间';


--
-- Name: COLUMN sys_permission.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.updated_by IS '更新人';


--
-- Name: COLUMN sys_permission.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_permission.deleted_at IS '删除时间';


--
-- Name: sys_permission_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_permission_id_seq OWNED BY dam_dpm.sys_permission.id;


--
-- Name: sys_role; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_role (
    id bigint NOT NULL,
    role_code character varying(30) NOT NULL,
    role_name character varying(100) NOT NULL,
    description character varying(500),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_role; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_role IS '角色表—— 角色定义，RBAC 主体之一';


--
-- Name: COLUMN sys_role.role_code; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role.role_code IS '角色编码';


--
-- Name: COLUMN sys_role.role_name; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role.role_name IS '角色名称';


--
-- Name: COLUMN sys_role.description; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role.description IS '角色描述';


--
-- Name: COLUMN sys_role.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role.status IS '状态';


--
-- Name: COLUMN sys_role.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role.created_at IS '创建时间';


--
-- Name: COLUMN sys_role.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role.created_by IS '创建人';


--
-- Name: COLUMN sys_role.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role.updated_at IS '更新时间';


--
-- Name: COLUMN sys_role.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role.updated_by IS '更新人';


--
-- Name: COLUMN sys_role.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role.deleted_at IS '删除时间';


--
-- Name: sys_role_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_role_id_seq OWNED BY dam_dpm.sys_role.id;


--
-- Name: sys_role_menu; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_role_menu (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_role_menu; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_role_menu IS '角色菜单关联表—— 角色-菜单关联';


--
-- Name: COLUMN sys_role_menu.role_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_menu.role_id IS '角色ID';


--
-- Name: COLUMN sys_role_menu.menu_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_menu.menu_id IS '菜单ID';


--
-- Name: COLUMN sys_role_menu.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_menu.status IS '状态';


--
-- Name: COLUMN sys_role_menu.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_menu.created_at IS '创建时间';


--
-- Name: COLUMN sys_role_menu.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_menu.created_by IS '创建人';


--
-- Name: COLUMN sys_role_menu.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_menu.updated_at IS '更新时间';


--
-- Name: COLUMN sys_role_menu.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_menu.updated_by IS '更新人';


--
-- Name: COLUMN sys_role_menu.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_menu.deleted_at IS '删除时间';


--
-- Name: sys_role_menu_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_role_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_menu_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_role_menu_id_seq OWNED BY dam_dpm.sys_role_menu.id;


--
-- Name: sys_role_permission; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_role_permission (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_role_permission; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_role_permission IS '角色权限关联表—— 角色-权限关联';


--
-- Name: COLUMN sys_role_permission.role_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_permission.role_id IS '角色ID';


--
-- Name: COLUMN sys_role_permission.permission_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_permission.permission_id IS '权限ID';


--
-- Name: COLUMN sys_role_permission.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_permission.status IS '状态';


--
-- Name: COLUMN sys_role_permission.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_permission.created_at IS '创建时间';


--
-- Name: COLUMN sys_role_permission.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_permission.created_by IS '创建人';


--
-- Name: COLUMN sys_role_permission.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_permission.updated_at IS '更新时间';


--
-- Name: COLUMN sys_role_permission.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_permission.updated_by IS '更新人';


--
-- Name: COLUMN sys_role_permission.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_role_permission.deleted_at IS '删除时间';


--
-- Name: sys_role_permission_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_role_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_role_permission_id_seq OWNED BY dam_dpm.sys_role_permission.id;


--
-- Name: sys_role_subject_domain; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_role_subject_domain (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    subject_domain_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_role_subject_domain; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_role_subject_domain IS '角色可查看的资产主题域（未配置的角色默认可见全部）';


--
-- Name: sys_role_subject_domain_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_role_subject_domain_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_role_subject_domain_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_role_subject_domain_id_seq OWNED BY dam_dpm.sys_role_subject_domain.id;


--
-- Name: sys_user; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_user (
    id bigint NOT NULL,
    username character varying(50) NOT NULL,
    password character varying(255) NOT NULL,
    real_name character varying(100),
    email character varying(100),
    phone character varying(20),
    department character varying(100) NOT NULL,
    avatar character varying(255),
    last_login_at timestamp without time zone NOT NULL,
    last_login_ip character varying(20) NOT NULL,
    password_hash character varying(64) NOT NULL,
    salt character varying(32) NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_user; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_user IS '用户表—— 平台用户（融合现平台密码哈希/盐/最后登录字段）';


--
-- Name: COLUMN sys_user.username; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.username IS '用户名';


--
-- Name: COLUMN sys_user.password; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.password IS '密码(BCrypt加密)';


--
-- Name: COLUMN sys_user.real_name; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.real_name IS '真实姓名';


--
-- Name: COLUMN sys_user.email; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.email IS '邮箱';


--
-- Name: COLUMN sys_user.phone; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.phone IS '手机号';


--
-- Name: COLUMN sys_user.department; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.department IS '所属科室/部门';


--
-- Name: COLUMN sys_user.avatar; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.avatar IS '头像';


--
-- Name: COLUMN sys_user.last_login_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.last_login_at IS '最后登录时间';


--
-- Name: COLUMN sys_user.last_login_ip; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.last_login_ip IS '最后登录IP';


--
-- Name: COLUMN sys_user.password_hash; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.password_hash IS '密码哈希（SHA-256 + 盐，融合自现平台）';


--
-- Name: COLUMN sys_user.salt; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.salt IS '密码盐值（融合自现平台）';


--
-- Name: COLUMN sys_user.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.status IS '状态';


--
-- Name: COLUMN sys_user.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.created_at IS '创建时间';


--
-- Name: COLUMN sys_user.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.created_by IS '创建人';


--
-- Name: COLUMN sys_user.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.updated_at IS '更新时间';


--
-- Name: COLUMN sys_user.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.updated_by IS '更新人';


--
-- Name: COLUMN sys_user.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user.deleted_at IS '删除时间';


--
-- Name: sys_user_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_user_id_seq OWNED BY dam_dpm.sys_user.id;


--
-- Name: sys_user_role; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.sys_user_role (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    default_role character varying(2) DEFAULT 'Y'::character varying NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE sys_user_role; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.sys_user_role IS '用户角色关联表—— 用户-角色多对多关联';


--
-- Name: COLUMN sys_user_role.user_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user_role.user_id IS '用户ID';


--
-- Name: COLUMN sys_user_role.role_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user_role.role_id IS '角色ID';


--
-- Name: COLUMN sys_user_role.default_role; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user_role.default_role IS '是否默认角色';


--
-- Name: COLUMN sys_user_role.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user_role.status IS '状态';


--
-- Name: COLUMN sys_user_role.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user_role.created_at IS '创建时间';


--
-- Name: COLUMN sys_user_role.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user_role.created_by IS '创建人';


--
-- Name: COLUMN sys_user_role.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user_role.updated_at IS '更新时间';


--
-- Name: COLUMN sys_user_role.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user_role.updated_by IS '更新人';


--
-- Name: COLUMN sys_user_role.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.sys_user_role.deleted_at IS '删除时间';


--
-- Name: sys_user_role_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.sys_user_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_role_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.sys_user_role_id_seq OWNED BY dam_dpm.sys_user_role.id;


--
-- Name: ticket_comment; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.ticket_comment (
    id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    parent_comment_id bigint,
    content text NOT NULL,
    attachments jsonb,
    mentioned_users text[],
    is_internal boolean,
    commenter_id character varying(100) NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE ticket_comment; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.ticket_comment IS '工单评论表—— 工单评论';


--
-- Name: COLUMN ticket_comment.ticket_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.ticket_id IS '工单ID';


--
-- Name: COLUMN ticket_comment.parent_comment_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.parent_comment_id IS '父评论ID';


--
-- Name: COLUMN ticket_comment.content; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.content IS '评论内容';


--
-- Name: COLUMN ticket_comment.attachments; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.attachments IS '附件列表';


--
-- Name: COLUMN ticket_comment.mentioned_users; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.mentioned_users IS '@提及的用户ID列表';


--
-- Name: COLUMN ticket_comment.is_internal; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.is_internal IS '是否内部评论';


--
-- Name: COLUMN ticket_comment.commenter_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.commenter_id IS '评论人ID';


--
-- Name: COLUMN ticket_comment.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.status IS '状态';


--
-- Name: COLUMN ticket_comment.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.created_at IS '创建时间';


--
-- Name: COLUMN ticket_comment.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.created_by IS '创建人';


--
-- Name: COLUMN ticket_comment.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.updated_at IS '更新时间';


--
-- Name: COLUMN ticket_comment.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.updated_by IS '更新人';


--
-- Name: COLUMN ticket_comment.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_comment.deleted_at IS '删除时间';


--
-- Name: ticket_comment_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.ticket_comment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ticket_comment_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.ticket_comment_id_seq OWNED BY dam_dpm.ticket_comment.id;


--
-- Name: ticket_flow_log; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.ticket_flow_log (
    id bigint NOT NULL,
    ticket_id integer NOT NULL,
    from_status character varying(50) NOT NULL,
    to_status character varying(50) NOT NULL,
    operator_id integer NOT NULL,
    operation_type character varying(50) NOT NULL,
    comment character varying(500),
    attachments jsonb,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE ticket_flow_log; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.ticket_flow_log IS '工单状态流转记录表—— 工单状态流转记录';


--
-- Name: COLUMN ticket_flow_log.ticket_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.ticket_id IS '工单ID';


--
-- Name: COLUMN ticket_flow_log.from_status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.from_status IS '变更前状态';


--
-- Name: COLUMN ticket_flow_log.to_status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.to_status IS '变更后状态';


--
-- Name: COLUMN ticket_flow_log.operator_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.operator_id IS '操作人ID';


--
-- Name: COLUMN ticket_flow_log.operation_type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.operation_type IS '操作类型';


--
-- Name: COLUMN ticket_flow_log.comment; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.comment IS '备注';


--
-- Name: COLUMN ticket_flow_log.attachments; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.attachments IS '附件';


--
-- Name: COLUMN ticket_flow_log.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.status IS '状态';


--
-- Name: COLUMN ticket_flow_log.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.created_at IS '创建时间';


--
-- Name: COLUMN ticket_flow_log.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.created_by IS '创建人';


--
-- Name: COLUMN ticket_flow_log.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.updated_at IS '更新时间';


--
-- Name: COLUMN ticket_flow_log.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.updated_by IS '更新人';


--
-- Name: COLUMN ticket_flow_log.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_flow_log.deleted_at IS '删除时间';


--
-- Name: ticket_flow_log_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.ticket_flow_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ticket_flow_log_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.ticket_flow_log_id_seq OWNED BY dam_dpm.ticket_flow_log.id;


--
-- Name: ticket_order; Type: TABLE; Schema: dam_dpm; Owner: -
--

CREATE TABLE dam_dpm.ticket_order (
    id bigint NOT NULL,
    ticket_no character varying(50) NOT NULL,
    ticket_type character varying(50) NOT NULL,
    title character varying(100) NOT NULL,
    description text,
    priority character varying(100) NOT NULL,
    applicant_id integer NOT NULL,
    assignee_id integer,
    related_asset_id integer,
    related_application_id integer,
    expected_complete_time timestamp without time zone,
    actual_complete_time timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE ticket_order; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON TABLE dam_dpm.ticket_order IS '工单主表—— 工单主表（数据申请/质量问题/变更申请）';


--
-- Name: COLUMN ticket_order.ticket_no; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.ticket_no IS '工单编号';


--
-- Name: COLUMN ticket_order.ticket_type; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.ticket_type IS '工单类型';


--
-- Name: COLUMN ticket_order.title; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.title IS '工单标题';


--
-- Name: COLUMN ticket_order.description; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.description IS '描述';


--
-- Name: COLUMN ticket_order.priority; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.priority IS '优先级';


--
-- Name: COLUMN ticket_order.applicant_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.applicant_id IS '申请人ID';


--
-- Name: COLUMN ticket_order.assignee_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.assignee_id IS '处理人ID';


--
-- Name: COLUMN ticket_order.related_asset_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.related_asset_id IS '关联资产ID';


--
-- Name: COLUMN ticket_order.related_application_id; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.related_application_id IS '关联申请单ID';


--
-- Name: COLUMN ticket_order.expected_complete_time; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.expected_complete_time IS '期望完成时间';


--
-- Name: COLUMN ticket_order.actual_complete_time; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.actual_complete_time IS '实际完成时间';


--
-- Name: COLUMN ticket_order.status; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.status IS '状态';


--
-- Name: COLUMN ticket_order.created_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.created_at IS '创建时间';


--
-- Name: COLUMN ticket_order.created_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.created_by IS '创建人';


--
-- Name: COLUMN ticket_order.updated_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.updated_at IS '更新时间';


--
-- Name: COLUMN ticket_order.updated_by; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.updated_by IS '更新人';


--
-- Name: COLUMN ticket_order.deleted_at; Type: COMMENT; Schema: dam_dpm; Owner: -
--

COMMENT ON COLUMN dam_dpm.ticket_order.deleted_at IS '删除时间';


--
-- Name: ticket_order_id_seq; Type: SEQUENCE; Schema: dam_dpm; Owner: -
--

CREATE SEQUENCE dam_dpm.ticket_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ticket_order_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpm; Owner: -
--

ALTER SEQUENCE dam_dpm.ticket_order_id_seq OWNED BY dam_dpm.ticket_order.id;


--
-- Name: code_crosswalk id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.code_crosswalk ALTER COLUMN id SET DEFAULT nextval('dam_dgp.code_crosswalk_id_seq'::regclass);


--
-- Name: data_standard id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.data_standard ALTER COLUMN id SET DEFAULT nextval('dam_dgp.data_standard_id_seq'::regclass);


--
-- Name: security_access_application id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_access_application ALTER COLUMN id SET DEFAULT nextval('dam_dgp.security_access_application_id_seq'::regclass);


--
-- Name: security_data_audit_log id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_data_audit_log ALTER COLUMN id SET DEFAULT nextval('dam_dgp.security_data_audit_log_id_seq'::regclass);


--
-- Name: security_data_permission id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_data_permission ALTER COLUMN id SET DEFAULT nextval('dam_dgp.security_data_permission_id_seq'::regclass);


--
-- Name: security_masking_policy id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_masking_policy ALTER COLUMN id SET DEFAULT nextval('dam_dgp.security_masking_policy_id_seq'::regclass);


--
-- Name: security_sensitive_discovery id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_sensitive_discovery ALTER COLUMN id SET DEFAULT nextval('dam_dgp.security_sensitive_discovery_id_seq'::regclass);


--
-- Name: security_sensitive_rule id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_sensitive_rule ALTER COLUMN id SET DEFAULT nextval('dam_dgp.security_sensitive_rule_id_seq'::regclass);


--
-- Name: standard_category id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_category ALTER COLUMN id SET DEFAULT nextval('dam_dgp.standard_category_id_seq'::regclass);


--
-- Name: standard_code_table id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_code_table ALTER COLUMN id SET DEFAULT nextval('dam_dgp.standard_code_table_id_seq'::regclass);


--
-- Name: standard_code_value id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_code_value ALTER COLUMN id SET DEFAULT nextval('dam_dgp.standard_code_value_id_seq'::regclass);


--
-- Name: standard_deviation_report id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_deviation_report ALTER COLUMN id SET DEFAULT nextval('dam_dgp.standard_deviation_report_id_seq'::regclass);


--
-- Name: standard_mapping id; Type: DEFAULT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_mapping ALTER COLUMN id SET DEFAULT nextval('dam_dgp.standard_mapping_id_seq'::regclass);


--
-- Name: sched_job id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sched_job ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sched_job_id_seq'::regclass);


--
-- Name: sched_job_log id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sched_job_log ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sched_job_log_id_seq'::regclass);


--
-- Name: sys_audit_log id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_audit_log ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_audit_log_id_seq'::regclass);


--
-- Name: sys_config id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_config ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_config_id_seq'::regclass);


--
-- Name: sys_dict_data id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_dict_data ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_dict_data_id_seq'::regclass);


--
-- Name: sys_dict_type id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_dict_type ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_dict_type_id_seq'::regclass);


--
-- Name: sys_menu id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_menu ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_menu_id_seq'::regclass);


--
-- Name: sys_permission id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_permission ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_permission_id_seq'::regclass);


--
-- Name: sys_role id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_role ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_role_id_seq'::regclass);


--
-- Name: sys_role_menu id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_role_menu ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_role_menu_id_seq'::regclass);


--
-- Name: sys_role_permission id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_role_permission ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_role_permission_id_seq'::regclass);


--
-- Name: sys_role_subject_domain id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_role_subject_domain ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_role_subject_domain_id_seq'::regclass);


--
-- Name: sys_user id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_user ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_user_id_seq'::regclass);


--
-- Name: sys_user_role id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_user_role ALTER COLUMN id SET DEFAULT nextval('dam_dpm.sys_user_role_id_seq'::regclass);


--
-- Name: ticket_comment id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.ticket_comment ALTER COLUMN id SET DEFAULT nextval('dam_dpm.ticket_comment_id_seq'::regclass);


--
-- Name: ticket_flow_log id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.ticket_flow_log ALTER COLUMN id SET DEFAULT nextval('dam_dpm.ticket_flow_log_id_seq'::regclass);


--
-- Name: ticket_order id; Type: DEFAULT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.ticket_order ALTER COLUMN id SET DEFAULT nextval('dam_dpm.ticket_order_id_seq'::regclass);


--
-- Name: code_crosswalk code_crosswalk_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.code_crosswalk
    ADD CONSTRAINT code_crosswalk_pkey PRIMARY KEY (id);


--
-- Name: data_standard data_standard_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.data_standard
    ADD CONSTRAINT data_standard_pkey PRIMARY KEY (id);


--
-- Name: security_access_application security_access_application_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_access_application
    ADD CONSTRAINT security_access_application_pkey PRIMARY KEY (id);


--
-- Name: security_data_audit_log security_data_audit_log_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_data_audit_log
    ADD CONSTRAINT security_data_audit_log_pkey PRIMARY KEY (id);


--
-- Name: security_data_permission security_data_permission_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_data_permission
    ADD CONSTRAINT security_data_permission_pkey PRIMARY KEY (id);


--
-- Name: security_masking_policy security_masking_policy_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_masking_policy
    ADD CONSTRAINT security_masking_policy_pkey PRIMARY KEY (id);


--
-- Name: security_sensitive_discovery security_sensitive_discovery_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_sensitive_discovery
    ADD CONSTRAINT security_sensitive_discovery_pkey PRIMARY KEY (id);


--
-- Name: security_sensitive_rule security_sensitive_rule_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.security_sensitive_rule
    ADD CONSTRAINT security_sensitive_rule_pkey PRIMARY KEY (id);


--
-- Name: standard_category standard_category_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_category
    ADD CONSTRAINT standard_category_pkey PRIMARY KEY (id);


--
-- Name: standard_code_table standard_code_table_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_code_table
    ADD CONSTRAINT standard_code_table_pkey PRIMARY KEY (id);


--
-- Name: standard_code_value standard_code_value_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_code_value
    ADD CONSTRAINT standard_code_value_pkey PRIMARY KEY (id);


--
-- Name: standard_deviation_report standard_deviation_report_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_deviation_report
    ADD CONSTRAINT standard_deviation_report_pkey PRIMARY KEY (id);


--
-- Name: standard_mapping standard_mapping_pkey; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.standard_mapping
    ADD CONSTRAINT standard_mapping_pkey PRIMARY KEY (id);


--
-- Name: data_standard uq_data_standard_code; Type: CONSTRAINT; Schema: dam_dgp; Owner: -
--

ALTER TABLE ONLY dam_dgp.data_standard
    ADD CONSTRAINT uq_data_standard_code UNIQUE (code);


--
-- Name: sched_job_log sched_job_log_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sched_job_log
    ADD CONSTRAINT sched_job_log_pkey PRIMARY KEY (id);


--
-- Name: sched_job sched_job_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sched_job
    ADD CONSTRAINT sched_job_pkey PRIMARY KEY (id);


--
-- Name: sys_audit_log sys_audit_log_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_audit_log
    ADD CONSTRAINT sys_audit_log_pkey PRIMARY KEY (id);


--
-- Name: sys_config sys_config_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_config
    ADD CONSTRAINT sys_config_pkey PRIMARY KEY (id);


--
-- Name: sys_dict_data sys_dict_data_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_dict_data
    ADD CONSTRAINT sys_dict_data_pkey PRIMARY KEY (id);


--
-- Name: sys_dict_type sys_dict_type_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_dict_type
    ADD CONSTRAINT sys_dict_type_pkey PRIMARY KEY (id);


--
-- Name: sys_menu sys_menu_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_menu
    ADD CONSTRAINT sys_menu_pkey PRIMARY KEY (id);


--
-- Name: sys_permission sys_permission_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_permission
    ADD CONSTRAINT sys_permission_pkey PRIMARY KEY (id);


--
-- Name: sys_role_menu sys_role_menu_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_role_menu
    ADD CONSTRAINT sys_role_menu_pkey PRIMARY KEY (id);


--
-- Name: sys_role_permission sys_role_permission_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_role_permission
    ADD CONSTRAINT sys_role_permission_pkey PRIMARY KEY (id);


--
-- Name: sys_role sys_role_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_role
    ADD CONSTRAINT sys_role_pkey PRIMARY KEY (id);


--
-- Name: sys_role_subject_domain sys_role_subject_domain_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_role_subject_domain
    ADD CONSTRAINT sys_role_subject_domain_pkey PRIMARY KEY (id);


--
-- Name: sys_user sys_user_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_user
    ADD CONSTRAINT sys_user_pkey PRIMARY KEY (id);


--
-- Name: sys_user_role sys_user_role_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sys_user_role
    ADD CONSTRAINT sys_user_role_pkey PRIMARY KEY (id);


--
-- Name: ticket_comment ticket_comment_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.ticket_comment
    ADD CONSTRAINT ticket_comment_pkey PRIMARY KEY (id);


--
-- Name: ticket_flow_log ticket_flow_log_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.ticket_flow_log
    ADD CONSTRAINT ticket_flow_log_pkey PRIMARY KEY (id);


--
-- Name: ticket_order ticket_order_pkey; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.ticket_order
    ADD CONSTRAINT ticket_order_pkey PRIMARY KEY (id);


--
-- Name: sched_job uq_sched_job_job_code; Type: CONSTRAINT; Schema: dam_dpm; Owner: -
--

ALTER TABLE ONLY dam_dpm.sched_job
    ADD CONSTRAINT uq_sched_job_job_code UNIQUE (job_code);


--
-- Name: idx_code_crosswalk_table; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_code_crosswalk_table ON dam_dgp.code_crosswalk USING btree (code_table_code, source_system) WHERE (deleted_at IS NULL);


--
-- Name: idx_data_standard_category_id; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_data_standard_category_id ON dam_dgp.data_standard USING btree (category_id);


--
-- Name: idx_security_access_application_asset_id; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_security_access_application_asset_id ON dam_dgp.security_access_application USING btree (asset_id);


--
-- Name: idx_security_data_audit_log_asset_id; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_security_data_audit_log_asset_id ON dam_dgp.security_data_audit_log USING btree (asset_id);


--
-- Name: idx_security_data_audit_log_table_id; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_security_data_audit_log_table_id ON dam_dgp.security_data_audit_log USING btree (table_id);


--
-- Name: idx_security_masking_policy_target_id; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_security_masking_policy_target_id ON dam_dgp.security_masking_policy USING btree (target_id);


--
-- Name: idx_security_masking_policy_target_type; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_security_masking_policy_target_type ON dam_dgp.security_masking_policy USING btree (target_type);


--
-- Name: idx_security_sensitive_rule_dictionary_id; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_security_sensitive_rule_dictionary_id ON dam_dgp.security_sensitive_rule USING btree (dictionary_id);


--
-- Name: idx_standard_code_value_code_table_id; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_standard_code_value_code_table_id ON dam_dgp.standard_code_value USING btree (code_table_id);


--
-- Name: idx_standard_deviation_report_report_no; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_standard_deviation_report_report_no ON dam_dgp.standard_deviation_report USING btree (report_no);


--
-- Name: idx_standard_deviation_report_standard_code_table_id; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_standard_deviation_report_standard_code_table_id ON dam_dgp.standard_deviation_report USING btree (standard_code_table_id);


--
-- Name: idx_standard_deviation_report_table_id; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE INDEX idx_standard_deviation_report_table_id ON dam_dgp.standard_deviation_report USING btree (table_id);


--
-- Name: uk_code_crosswalk; Type: INDEX; Schema: dam_dgp; Owner: -
--

CREATE UNIQUE INDEX uk_code_crosswalk ON dam_dgp.code_crosswalk USING btree (code_table_id, source_system, source_value) WHERE (deleted_at IS NULL);


--
-- Name: idx_sched_job_log_job_id; Type: INDEX; Schema: dam_dpm; Owner: -
--

CREATE INDEX idx_sched_job_log_job_id ON dam_dpm.sched_job_log USING btree (job_id);


--
-- Name: idx_sched_job_log_started_at; Type: INDEX; Schema: dam_dpm; Owner: -
--

CREATE INDEX idx_sched_job_log_started_at ON dam_dpm.sched_job_log USING btree (started_at);


--
-- Name: idx_sys_audit_log_target_id; Type: INDEX; Schema: dam_dpm; Owner: -
--

CREATE INDEX idx_sys_audit_log_target_id ON dam_dpm.sys_audit_log USING btree (target_id);


--
-- Name: idx_sys_audit_log_target_type; Type: INDEX; Schema: dam_dpm; Owner: -
--

CREATE INDEX idx_sys_audit_log_target_type ON dam_dpm.sys_audit_log USING btree (target_type);


--
-- Name: idx_sys_menu_parent; Type: INDEX; Schema: dam_dpm; Owner: -
--

CREATE INDEX idx_sys_menu_parent ON dam_dpm.sys_menu USING btree (parent_id);


--
-- Name: idx_sys_menu_path; Type: INDEX; Schema: dam_dpm; Owner: -
--

CREATE INDEX idx_sys_menu_path ON dam_dpm.sys_menu USING btree (path);


--
-- Name: idx_ticket_order_related_application_id; Type: INDEX; Schema: dam_dpm; Owner: -
--

CREATE INDEX idx_ticket_order_related_application_id ON dam_dpm.ticket_order USING btree (related_application_id);


--
-- Name: idx_ticket_order_related_asset_id; Type: INDEX; Schema: dam_dpm; Owner: -
--

CREATE INDEX idx_ticket_order_related_asset_id ON dam_dpm.ticket_order USING btree (related_asset_id);


--
-- Name: uk_role_subject_domain; Type: INDEX; Schema: dam_dpm; Owner: -
--

CREATE UNIQUE INDEX uk_role_subject_domain ON dam_dpm.sys_role_subject_domain USING btree (role_id, subject_domain_id) WHERE (deleted_at IS NULL);


--
-- PostgreSQL database dump complete
--

