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
-- Name: PROD; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA "PROD";


--
-- Name: PROD.CIS_Model; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA "PROD.CIS_Model";


--
-- Name: ads; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA ads;


--
-- Name: SCHEMA ads; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA ads IS 'ADS 应用层：面向报表、大屏与数据服务的最终结果集';


--
-- Name: dam_ai; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_ai;


--
-- Name: dam_asset; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_asset;


--
-- Name: dam_dam; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dam;


--
-- Name: dam_dan; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dan;


--
-- Name: dam_dap; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dap;


--
-- Name: dam_dapp; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dapp;


--
-- Name: dam_dgp; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dgp;


--
-- Name: dam_dimension; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dimension;


--
-- Name: dam_dpm; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dpm;


--
-- Name: dam_dpp; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dpp;


--
-- Name: dam_dsp; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_dsp;


--
-- Name: dam_knowledge; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_knowledge;


--
-- Name: dam_metadata; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_metadata;


--
-- Name: dam_metric; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_metric;


--
-- Name: dam_model; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_model;


--
-- Name: dam_report; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_report;


--
-- Name: dam_sched; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_sched;


--
-- Name: dam_security; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_security;


--
-- Name: dam_service; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_service;


--
-- Name: dam_standard; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_standard;


--
-- Name: dam_sys; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_sys;


--
-- Name: dam_ticket; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dam_ticket;


--
-- Name: dim; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dim;


--
-- Name: SCHEMA dim; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA dim IS 'DIM 维度层：医院字典与码表（组织/人口学/诊断/手术/药学等 14 个维度域）';


--
-- Name: dwd; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dwd;


--
-- Name: SCHEMA dwd; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA dwd IS 'DWD 明细层：按业务主题域组织的清洗明细（患者/就诊/诊断/医嘱/处方/检验/检查/体征/手术等）';


--
-- Name: dws; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA dws;


--
-- Name: SCHEMA dws; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA dws IS 'DWS 汇总层：轻度聚合指标（按日/科室/医生等粒度）';


--
-- Name: hospital_demo; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA hospital_demo;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: OE_OrdExec; Type: TABLE; Schema: PROD.CIS_Model; Owner: -
--

CREATE TABLE "PROD.CIS_Model"."OE_OrdExec" (
    execid character varying(32) NOT NULL,
    ordid character varying(32),
    execdate timestamp without time zone,
    nurseid character varying(64),
    deptid character varying(16),
    execstatus character varying(8),
    note character varying(200)
);


--
-- Name: OE_Order; Type: TABLE; Schema: PROD.CIS_Model; Owner: -
--

CREATE TABLE "PROD.CIS_Model"."OE_Order" (
    ordid character varying(32) NOT NULL,
    admid character varying(32),
    patid character varying(32),
    ordtype character varying(8),
    arcimcode character varying(32),
    arcimdesc character varying(200),
    spec character varying(100),
    dosage character varying(100),
    frequency character varying(32),
    route character varying(32),
    doctorname character varying(64),
    orddate timestamp without time zone,
    ordstatus character varying(16)
);


--
-- Name: PA_Adm; Type: TABLE; Schema: PROD.CIS_Model; Owner: -
--

CREATE TABLE "PROD.CIS_Model"."PA_Adm" (
    admid character varying(32) NOT NULL,
    patid character varying(32),
    admtype character varying(8),
    deptid character varying(16),
    deptname character varying(64),
    doctorid character varying(32),
    doctorname character varying(64),
    admdate timestamp without time zone,
    dischdate timestamp without time zone,
    admstatus character varying(8),
    totalcost numeric(12,2)
);


--
-- Name: PA_PatMas; Type: TABLE; Schema: PROD.CIS_Model; Owner: -
--

CREATE TABLE "PROD.CIS_Model"."PA_PatMas" (
    patid character varying(32) NOT NULL,
    name character varying(64),
    sex character varying(8),
    dateofbirth date,
    idno character varying(32),
    maritalstatus character varying(8),
    occupation character varying(64),
    regdate timestamp without time zone
);


--
-- Name: asset_category; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.asset_category (
    id bigint NOT NULL,
    category_code character varying(50) NOT NULL,
    category_name character varying(100) NOT NULL,
    parent_id bigint,
    tree_level bigint NOT NULL,
    category_path character varying(500) NOT NULL,
    description character varying(500),
    sort_order bigint NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE asset_category; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.asset_category IS '资产分类表—— 资产分类树（业务/技术/主题三维分类）';


--
-- Name: COLUMN asset_category.category_code; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.category_code IS '分类编码';


--
-- Name: COLUMN asset_category.category_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.category_name IS '分类名称';


--
-- Name: COLUMN asset_category.parent_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.parent_id IS '父分类ID';


--
-- Name: COLUMN asset_category.tree_level; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.tree_level IS '树层级';


--
-- Name: COLUMN asset_category.category_path; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.category_path IS '分类全路径';


--
-- Name: COLUMN asset_category.description; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.description IS '分类描述';


--
-- Name: COLUMN asset_category.sort_order; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.sort_order IS '排序序号';


--
-- Name: COLUMN asset_category.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.status IS '状态';


--
-- Name: COLUMN asset_category.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.created_at IS '创建时间';


--
-- Name: COLUMN asset_category.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.created_by IS '创建人';


--
-- Name: COLUMN asset_category.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.updated_at IS '更新时间';


--
-- Name: COLUMN asset_category.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.updated_by IS '更新人';


--
-- Name: COLUMN asset_category.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category.deleted_at IS '删除时间';


--
-- Name: asset_category_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.asset_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_category_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.asset_category_id_seq OWNED BY dam_dam.asset_category.id;


--
-- Name: asset_category_rel; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.asset_category_rel (
    id bigint NOT NULL,
    asset_id bigint NOT NULL,
    subject_domain_id bigint NOT NULL,
    category_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE asset_category_rel; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.asset_category_rel IS '资产-主题域-类目归属（一表可属多个主题域，同一主题域下唯一类目）';


--
-- Name: COLUMN asset_category_rel.asset_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category_rel.asset_id IS '资产ID（asset_info.id）';


--
-- Name: COLUMN asset_category_rel.subject_domain_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category_rel.subject_domain_id IS '一级主题域ID（asset_category.tree_level=1）';


--
-- Name: COLUMN asset_category_rel.category_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_category_rel.category_id IS '二级类目ID（asset_category.tree_level=2）';


--
-- Name: asset_category_rel_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.asset_category_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_category_rel_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.asset_category_rel_id_seq OWNED BY dam_dam.asset_category_rel.id;


--
-- Name: asset_favorite; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.asset_favorite (
    id bigint NOT NULL,
    asset_id integer NOT NULL,
    user_id integer NOT NULL,
    folder_name character varying(200) NOT NULL,
    favorite_time timestamp without time zone NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE asset_favorite; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.asset_favorite IS '资产收藏表—— 资产收藏';


--
-- Name: COLUMN asset_favorite.asset_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.asset_id IS '资产ID';


--
-- Name: COLUMN asset_favorite.user_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.user_id IS '用户ID';


--
-- Name: COLUMN asset_favorite.folder_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.folder_name IS '收藏夹名称';


--
-- Name: COLUMN asset_favorite.favorite_time; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.favorite_time IS '收藏时间';


--
-- Name: COLUMN asset_favorite.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.status IS '状态';


--
-- Name: COLUMN asset_favorite.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.created_at IS '创建时间';


--
-- Name: COLUMN asset_favorite.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.created_by IS '创建人';


--
-- Name: COLUMN asset_favorite.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.updated_at IS '更新时间';


--
-- Name: COLUMN asset_favorite.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.updated_by IS '更新人';


--
-- Name: COLUMN asset_favorite.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_favorite.deleted_at IS '删除时间';


--
-- Name: asset_favorite_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.asset_favorite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_favorite_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.asset_favorite_id_seq OWNED BY dam_dam.asset_favorite.id;


--
-- Name: asset_info; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.asset_info (
    id bigint NOT NULL,
    asset_code character varying(100) NOT NULL,
    asset_name character varying(200) NOT NULL,
    asset_type_id integer NOT NULL,
    category_id integer NOT NULL,
    description text,
    business_owner_id integer,
    tech_owner_id integer,
    data_owner_id integer,
    source_system_id integer,
    data_source_id integer,
    source_metadata_type character varying(50),
    source_metadata_id integer,
    storage_location character varying(500),
    row_count bigint,
    storage_size bigint,
    quality_score numeric(5,2),
    popularity integer,
    published_status smallint,
    published_at timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE asset_info; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.asset_info IS '资产基本信息表—— 资产主表（业务描述/技术属性/负责人/安全等级/质量分）';


--
-- Name: COLUMN asset_info.asset_code; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.asset_code IS '资产编码';


--
-- Name: COLUMN asset_info.asset_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.asset_name IS '资产名称';


--
-- Name: COLUMN asset_info.asset_type_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.asset_type_id IS '资产类型ID';


--
-- Name: COLUMN asset_info.category_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.category_id IS '所属分类ID';


--
-- Name: COLUMN asset_info.description; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.description IS '资产描述';


--
-- Name: COLUMN asset_info.business_owner_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.business_owner_id IS '业务负责人ID';


--
-- Name: COLUMN asset_info.tech_owner_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.tech_owner_id IS '技术负责人ID';


--
-- Name: COLUMN asset_info.data_owner_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.data_owner_id IS '数据负责人ID';


--
-- Name: COLUMN asset_info.source_system_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.source_system_id IS '来源系统ID';


--
-- Name: COLUMN asset_info.data_source_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.data_source_id IS '数据源ID';


--
-- Name: COLUMN asset_info.source_metadata_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.source_metadata_type IS '元数据实体类型';


--
-- Name: COLUMN asset_info.source_metadata_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.source_metadata_id IS '元数据实体ID';


--
-- Name: COLUMN asset_info.storage_location; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.storage_location IS '存储位置';


--
-- Name: COLUMN asset_info.row_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.row_count IS '数据行数';


--
-- Name: COLUMN asset_info.storage_size; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.storage_size IS '存储大小（字节）';


--
-- Name: COLUMN asset_info.quality_score; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.quality_score IS '质量评分';


--
-- Name: COLUMN asset_info.popularity; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.popularity IS '热度（访问次数）';


--
-- Name: COLUMN asset_info.published_status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.published_status IS '发布状态';


--
-- Name: COLUMN asset_info.published_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.published_at IS '发布时间';


--
-- Name: COLUMN asset_info.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.status IS '状态';


--
-- Name: COLUMN asset_info.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.created_at IS '创建时间';


--
-- Name: COLUMN asset_info.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.created_by IS '创建人';


--
-- Name: COLUMN asset_info.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.updated_at IS '更新时间';


--
-- Name: COLUMN asset_info.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.updated_by IS '更新人';


--
-- Name: COLUMN asset_info.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_info.deleted_at IS '删除时间';


--
-- Name: asset_info_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.asset_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_info_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.asset_info_id_seq OWNED BY dam_dam.asset_info.id;


--
-- Name: asset_rating; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.asset_rating (
    id bigint NOT NULL,
    asset_id bigint NOT NULL,
    rating_score integer NOT NULL,
    rating_comment text NOT NULL,
    rating_dimension character varying(50) NOT NULL,
    user_id character varying(64) NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE asset_rating; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.asset_rating IS '资产评价表—— 资产评价（评分/评论）';


--
-- Name: COLUMN asset_rating.asset_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.asset_id IS '资产ID';


--
-- Name: COLUMN asset_rating.rating_score; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.rating_score IS '资产评分（1~5）';


--
-- Name: COLUMN asset_rating.rating_comment; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.rating_comment IS '评价内容';


--
-- Name: COLUMN asset_rating.rating_dimension; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.rating_dimension IS '评价维度';


--
-- Name: COLUMN asset_rating.user_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.user_id IS '评价人ID';


--
-- Name: COLUMN asset_rating.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.status IS '状态';


--
-- Name: COLUMN asset_rating.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.created_at IS '创建时间';


--
-- Name: COLUMN asset_rating.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.created_by IS '创建人';


--
-- Name: COLUMN asset_rating.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.updated_at IS '更新时间';


--
-- Name: COLUMN asset_rating.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.updated_by IS '更新人';


--
-- Name: COLUMN asset_rating.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_rating.deleted_at IS '删除时间';


--
-- Name: asset_rating_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.asset_rating_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_rating_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.asset_rating_id_seq OWNED BY dam_dam.asset_rating.id;


--
-- Name: asset_statistics; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.asset_statistics (
    id bigint NOT NULL,
    asset_id integer NOT NULL,
    stat_date timestamp without time zone NOT NULL,
    view_count integer NOT NULL,
    query_count integer NOT NULL,
    download_count integer NOT NULL,
    export_count integer NOT NULL,
    unique_users integer NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE asset_statistics; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.asset_statistics IS '资产使用统计表—— 资产使用统计（汇总 asset_usage_log）';


--
-- Name: COLUMN asset_statistics.asset_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.asset_id IS '资产ID';


--
-- Name: COLUMN asset_statistics.stat_date; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.stat_date IS '统计时间';


--
-- Name: COLUMN asset_statistics.view_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.view_count IS '浏览次数';


--
-- Name: COLUMN asset_statistics.query_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.query_count IS '查询次数';


--
-- Name: COLUMN asset_statistics.download_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.download_count IS '下载次数';


--
-- Name: COLUMN asset_statistics.export_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.export_count IS '导出次数';


--
-- Name: COLUMN asset_statistics.unique_users; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.unique_users IS '独立用户数';


--
-- Name: COLUMN asset_statistics.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.status IS '状态';


--
-- Name: COLUMN asset_statistics.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.created_at IS '创建时间';


--
-- Name: COLUMN asset_statistics.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.created_by IS '创建人';


--
-- Name: COLUMN asset_statistics.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.updated_at IS '更新时间';


--
-- Name: COLUMN asset_statistics.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.updated_by IS '更新人';


--
-- Name: COLUMN asset_statistics.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_statistics.deleted_at IS '删除时间';


--
-- Name: asset_statistics_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.asset_statistics_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_statistics_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.asset_statistics_id_seq OWNED BY dam_dam.asset_statistics.id;


--
-- Name: asset_tag; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.asset_tag (
    id bigint NOT NULL,
    tag_code character varying(100) NOT NULL,
    tag_name character varying(200) NOT NULL,
    tag_color character varying(20) NOT NULL,
    tag_scope character varying(20) NOT NULL,
    tag_type character varying(20) NOT NULL,
    description character varying(200),
    usage_count integer,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE asset_tag; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.asset_tag IS '标签定义表—— 标签定义';


--
-- Name: COLUMN asset_tag.tag_code; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.tag_code IS '标签代码';


--
-- Name: COLUMN asset_tag.tag_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.tag_name IS '标签名称';


--
-- Name: COLUMN asset_tag.tag_color; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.tag_color IS '标签颜色';


--
-- Name: COLUMN asset_tag.tag_scope; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.tag_scope IS '标签范围';


--
-- Name: COLUMN asset_tag.tag_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.tag_type IS '标签类型';


--
-- Name: COLUMN asset_tag.description; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.description IS '描述';


--
-- Name: COLUMN asset_tag.usage_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.usage_count IS '引用次数';


--
-- Name: COLUMN asset_tag.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.status IS '状态';


--
-- Name: COLUMN asset_tag.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.created_at IS '创建时间';


--
-- Name: COLUMN asset_tag.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.created_by IS '创建人';


--
-- Name: COLUMN asset_tag.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.updated_at IS '更新时间';


--
-- Name: COLUMN asset_tag.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.updated_by IS '更新人';


--
-- Name: COLUMN asset_tag.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag.deleted_at IS '删除时间';


--
-- Name: asset_tag_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.asset_tag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_tag_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.asset_tag_id_seq OWNED BY dam_dam.asset_tag.id;


--
-- Name: asset_tag_rel; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.asset_tag_rel (
    id bigint NOT NULL,
    asset_id integer NOT NULL,
    tag_id integer NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE asset_tag_rel; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.asset_tag_rel IS '资产标签关联表—— 资产-标签关联';


--
-- Name: COLUMN asset_tag_rel.asset_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag_rel.asset_id IS '资产ID';


--
-- Name: COLUMN asset_tag_rel.tag_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag_rel.tag_id IS '标签ID';


--
-- Name: COLUMN asset_tag_rel.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag_rel.status IS '状态';


--
-- Name: COLUMN asset_tag_rel.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag_rel.created_at IS '创建时间';


--
-- Name: COLUMN asset_tag_rel.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag_rel.created_by IS '创建人';


--
-- Name: COLUMN asset_tag_rel.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag_rel.updated_at IS '更新时间';


--
-- Name: COLUMN asset_tag_rel.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag_rel.updated_by IS '更新人';


--
-- Name: COLUMN asset_tag_rel.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_tag_rel.deleted_at IS '删除时间';


--
-- Name: asset_tag_rel_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.asset_tag_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_tag_rel_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.asset_tag_rel_id_seq OWNED BY dam_dam.asset_tag_rel.id;


--
-- Name: asset_usage_log; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.asset_usage_log (
    id bigint NOT NULL,
    asset_id integer NOT NULL,
    user_id character varying(50) NOT NULL,
    action_type character varying(50) NOT NULL,
    action_detail jsonb NOT NULL,
    ip_address character varying(50) NOT NULL,
    user_agent text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE asset_usage_log; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.asset_usage_log IS '资产使用日志表—— 资产使用日志（浏览/收藏/调用，支撑活跃度分析）';


--
-- Name: COLUMN asset_usage_log.asset_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.asset_id IS '资产ID';


--
-- Name: COLUMN asset_usage_log.user_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.user_id IS '用户ID';


--
-- Name: COLUMN asset_usage_log.action_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.action_type IS '操作类型';


--
-- Name: COLUMN asset_usage_log.action_detail; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.action_detail IS '操作详情';


--
-- Name: COLUMN asset_usage_log.ip_address; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.ip_address IS 'IP';


--
-- Name: COLUMN asset_usage_log.user_agent; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.user_agent IS '用户代理';


--
-- Name: COLUMN asset_usage_log.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.status IS '状态';


--
-- Name: COLUMN asset_usage_log.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.created_at IS '创建时间';


--
-- Name: COLUMN asset_usage_log.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.created_by IS '创建人';


--
-- Name: COLUMN asset_usage_log.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.updated_at IS '更新时间';


--
-- Name: COLUMN asset_usage_log.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.updated_by IS '更新人';


--
-- Name: COLUMN asset_usage_log.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.asset_usage_log.deleted_at IS '删除时间';


--
-- Name: asset_usage_log_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.asset_usage_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_usage_log_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.asset_usage_log_id_seq OWNED BY dam_dam.asset_usage_log.id;


--
-- Name: collect_task; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.collect_task (
    id bigint NOT NULL,
    task_code character varying(64) NOT NULL,
    task_name character varying(200) NOT NULL,
    source_schema character varying(64) NOT NULL,
    source_table character varying(64) NOT NULL,
    target_schema character varying(64) NOT NULL,
    target_table character varying(64) NOT NULL,
    column_mapping jsonb NOT NULL,
    uk_column character varying(64),
    load_mode character varying(20) DEFAULT 'UPSERT'::character varying NOT NULL,
    where_clause text,
    enabled boolean DEFAULT true NOT NULL,
    last_run_at timestamp without time zone,
    last_rows bigint,
    total_rows bigint DEFAULT 0 NOT NULL,
    last_status character varying(16),
    last_message text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone,
    source_ds_id bigint
);


--
-- Name: TABLE collect_task; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.collect_task IS 'HIS→数据中台采集任务表—— 源表到目标表的抽取映射与运行统计';


--
-- Name: COLUMN collect_task.source_ds_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.collect_task.source_ds_id IS '源数据源 id（data_source.id），非空表示跨库 JDBC 抽数任务';


--
-- Name: collect_task_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.collect_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: collect_task_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.collect_task_id_seq OWNED BY dam_dam.collect_task.id;


--
-- Name: collect_task_log; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.collect_task_log (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    started_at timestamp without time zone NOT NULL,
    finished_at timestamp without time zone NOT NULL,
    duration_ms bigint,
    rows_loaded bigint,
    exec_status character varying(16) NOT NULL,
    message text,
    trigger_by character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE collect_task_log; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.collect_task_log IS '采集任务运行记录—— 每次执行的行数/耗时/状态';


--
-- Name: collect_task_log_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.collect_task_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: collect_task_log_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.collect_task_log_id_seq OWNED BY dam_dam.collect_task_log.id;


--
-- Name: metadata_business; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_business (
    id bigint NOT NULL,
    business_code character varying(50) NOT NULL,
    business_name character varying(200) NOT NULL,
    business_definition text,
    business_owner_id bigint,
    data_domain_id bigint,
    business_domain_id bigint,
    business_process character varying(100),
    data_steward_id bigint,
    criticality_level character varying(50),
    data_quality_rules_id bigint,
    tags text[],
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_business; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_business IS '业务元数据表—— 业务元数据（业务定义/口径/负责人/数据管家/标签）';


--
-- Name: COLUMN metadata_business.business_code; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.business_code IS '业务代码';


--
-- Name: COLUMN metadata_business.business_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.business_name IS '业务名称';


--
-- Name: COLUMN metadata_business.business_definition; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.business_definition IS '业务定义/描述';


--
-- Name: COLUMN metadata_business.business_owner_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.business_owner_id IS '业务负责人';


--
-- Name: COLUMN metadata_business.data_domain_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.data_domain_id IS '数据域';


--
-- Name: COLUMN metadata_business.business_domain_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.business_domain_id IS '业务域';


--
-- Name: COLUMN metadata_business.business_process; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.business_process IS '业务流程';


--
-- Name: COLUMN metadata_business.data_steward_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.data_steward_id IS '数据管家ID';


--
-- Name: COLUMN metadata_business.criticality_level; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.criticality_level IS '业务重要性';


--
-- Name: COLUMN metadata_business.data_quality_rules_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.data_quality_rules_id IS '数据质量规则ID';


--
-- Name: COLUMN metadata_business.tags; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.tags IS '标签数组';


--
-- Name: COLUMN metadata_business.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.status IS '状态';


--
-- Name: COLUMN metadata_business.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.created_at IS '创建时间';


--
-- Name: COLUMN metadata_business.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.created_by IS '创建人';


--
-- Name: COLUMN metadata_business.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_business.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.updated_by IS '更新人';


--
-- Name: COLUMN metadata_business.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business.deleted_at IS '删除时间';


--
-- Name: metadata_business_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_business_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_business_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_business_id_seq OWNED BY dam_dam.metadata_business.id;


--
-- Name: metadata_business_table_rel; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_business_table_rel (
    id bigint NOT NULL,
    business_id bigint NOT NULL,
    table_id bigint NOT NULL,
    mapping_type_id character varying(200) NOT NULL,
    mapping_comment text NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_business_table_rel; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_business_table_rel IS '业务-表关联映射表—— 业务实体与技术表的映射关系';


--
-- Name: COLUMN metadata_business_table_rel.business_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.business_id IS '业务实体ID';


--
-- Name: COLUMN metadata_business_table_rel.table_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.table_id IS '技术元数据表ID';


--
-- Name: COLUMN metadata_business_table_rel.mapping_type_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.mapping_type_id IS '映射类型ID';


--
-- Name: COLUMN metadata_business_table_rel.mapping_comment; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.mapping_comment IS '映射说明';


--
-- Name: COLUMN metadata_business_table_rel.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.status IS '状态';


--
-- Name: COLUMN metadata_business_table_rel.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.created_at IS '创建时间';


--
-- Name: COLUMN metadata_business_table_rel.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.created_by IS '创建人';


--
-- Name: COLUMN metadata_business_table_rel.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_business_table_rel.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.updated_by IS '更新人';


--
-- Name: COLUMN metadata_business_table_rel.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_business_table_rel.deleted_at IS '删除时间';


--
-- Name: metadata_business_table_rel_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_business_table_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_business_table_rel_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_business_table_rel_id_seq OWNED BY dam_dam.metadata_business_table_rel.id;


--
-- Name: metadata_category; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_category (
    id bigint NOT NULL,
    category_name character varying(50) NOT NULL,
    category_code character varying(50) NOT NULL,
    category_type character varying(20) NOT NULL,
    parent_id bigint,
    tree_level integer NOT NULL,
    full_path character varying(500),
    icon character varying(500),
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
-- Name: TABLE metadata_category; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_category IS '元数据分类表—— 元数据分类树（技术/业务/管理三大类，支持多级）';


--
-- Name: COLUMN metadata_category.category_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.category_name IS '分类名称（如：技术元数据、业务元数据、管理元数据）';


--
-- Name: COLUMN metadata_category.category_code; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.category_code IS '分类编码（如：TECHNICAL、BUSINESS、MANAGEMENT），程序枚举引用';


--
-- Name: COLUMN metadata_category.category_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.category_type IS '分类层级类型：ROOT-根分类（一级）、SUB-子分类（二级及以下）';


--
-- Name: COLUMN metadata_category.parent_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.parent_id IS '父分类ID（根分类的parent_id为NULL）';


--
-- Name: COLUMN metadata_category.tree_level; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.tree_level IS '树层级（根为1，子类依次递增）';


--
-- Name: COLUMN metadata_category.full_path; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.full_path IS '分类全路径（如：/技术元数据/数据库表），便于前端面包屑展示';


--
-- Name: COLUMN metadata_category.icon; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.icon IS '图标名称（前端展示用）';


--
-- Name: COLUMN metadata_category.description; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.description IS '分类描述说明';


--
-- Name: COLUMN metadata_category.sort_order; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.sort_order IS '同级排序序号';


--
-- Name: COLUMN metadata_category.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.status IS '状态';


--
-- Name: COLUMN metadata_category.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.created_at IS '创建时间';


--
-- Name: COLUMN metadata_category.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.created_by IS '创建人';


--
-- Name: COLUMN metadata_category.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_category.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.updated_by IS '更新人';


--
-- Name: COLUMN metadata_category.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_category.deleted_at IS '删除时间';


--
-- Name: metadata_category_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_category_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_category_id_seq OWNED BY dam_dam.metadata_category.id;


--
-- Name: metadata_collection; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_collection (
    id bigint NOT NULL,
    datasource_id integer NOT NULL,
    schema_id bigint NOT NULL,
    job_name character varying(200),
    job_type character varying(100) NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    total_tables integer,
    total_columns integer,
    success_count integer,
    fail_count integer,
    error_message character varying(500),
    detail_log jsonb,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_collection; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_collection IS '元数据采集记录表—— 元数据采集任务记录（起止/总量/成败/错误/明细 JSONB）';


--
-- Name: COLUMN metadata_collection.datasource_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.datasource_id IS '数据源ID';


--
-- Name: COLUMN metadata_collection.schema_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.schema_id IS 'Schema ID（关联 metadata_schema.id，类型统一）';


--
-- Name: COLUMN metadata_collection.job_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.job_name IS '任务名称';


--
-- Name: COLUMN metadata_collection.job_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.job_type IS '任务类型';


--
-- Name: COLUMN metadata_collection.start_time; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.start_time IS '开始时间';


--
-- Name: COLUMN metadata_collection.end_time; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.end_time IS '结束时间';


--
-- Name: COLUMN metadata_collection.total_tables; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.total_tables IS '采集总表数';


--
-- Name: COLUMN metadata_collection.total_columns; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.total_columns IS '采集总字段数';


--
-- Name: COLUMN metadata_collection.success_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.success_count IS '成功数量';


--
-- Name: COLUMN metadata_collection.fail_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.fail_count IS '失败数量';


--
-- Name: COLUMN metadata_collection.error_message; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.error_message IS '错误日志';


--
-- Name: COLUMN metadata_collection.detail_log; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.detail_log IS '详细日志';


--
-- Name: COLUMN metadata_collection.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.status IS '状态';


--
-- Name: COLUMN metadata_collection.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.created_at IS '创建时间';


--
-- Name: COLUMN metadata_collection.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.created_by IS '创建人';


--
-- Name: COLUMN metadata_collection.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_collection.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.updated_by IS '更新人';


--
-- Name: COLUMN metadata_collection.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_collection.deleted_at IS '删除时间';


--
-- Name: metadata_collection_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_collection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_collection_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_collection_id_seq OWNED BY dam_dam.metadata_collection.id;


--
-- Name: metadata_column; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_column (
    id bigint NOT NULL,
    datasource_id bigint NOT NULL,
    schema_id bigint NOT NULL,
    table_id bigint NOT NULL,
    column_name character varying(100) NOT NULL,
    column_type character varying(100) NOT NULL,
    column_length integer,
    is_nullable boolean DEFAULT true NOT NULL,
    is_primary_key boolean,
    is_foreign_key boolean,
    default_value character varying(100),
    column_comment character varying(200),
    ordinal_position integer,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_column; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_column IS '技术元数据-字段—— 技术元数据-字段级（类型/主外键/默认值/序位）';


--
-- Name: COLUMN metadata_column.datasource_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.datasource_id IS '所属数据源ID';


--
-- Name: COLUMN metadata_column.schema_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.schema_id IS 'Schema ID（关联 metadata_schema.id，类型统一 int8）';


--
-- Name: COLUMN metadata_column.table_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.table_id IS '所属表ID';


--
-- Name: COLUMN metadata_column.column_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.column_name IS '字段名（非空约束补齐）';


--
-- Name: COLUMN metadata_column.column_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.column_type IS '字段类型';


--
-- Name: COLUMN metadata_column.column_length; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.column_length IS '字段长度';


--
-- Name: COLUMN metadata_column.is_nullable; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.is_nullable IS '是否可空';


--
-- Name: COLUMN metadata_column.is_primary_key; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.is_primary_key IS '是否主键';


--
-- Name: COLUMN metadata_column.is_foreign_key; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.is_foreign_key IS '是否外键';


--
-- Name: COLUMN metadata_column.default_value; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.default_value IS '默认值';


--
-- Name: COLUMN metadata_column.column_comment; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.column_comment IS '字段注释';


--
-- Name: COLUMN metadata_column.ordinal_position; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.ordinal_position IS '字段顺序';


--
-- Name: COLUMN metadata_column.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.status IS '状态';


--
-- Name: COLUMN metadata_column.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.created_at IS '创建时间';


--
-- Name: COLUMN metadata_column.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.created_by IS '创建人';


--
-- Name: COLUMN metadata_column.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_column.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.updated_by IS '更新人';


--
-- Name: COLUMN metadata_column.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_column.deleted_at IS '删除时间';


--
-- Name: metadata_column_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_column_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_column_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_column_id_seq OWNED BY dam_dam.metadata_column.id;


--
-- Name: metadata_datasource; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_datasource (
    id bigint NOT NULL,
    ds_name character varying(100) NOT NULL,
    ds_code character varying(100) NOT NULL,
    ds_type character varying(50) NOT NULL,
    ds_adapter character varying(100) NOT NULL,
    ds_ip character varying(20),
    ds_port character varying(10),
    jdbc_url character varying(200) NOT NULL,
    username character varying(100) NOT NULL,
    password character varying(100) NOT NULL,
    driver_class character varying(200) NOT NULL,
    connection_props character varying(200),
    description character varying(200),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_datasource; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_datasource IS '数据源表—— 数据源登记（连接信息，密码加密存储）';


--
-- Name: COLUMN metadata_datasource.ds_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.ds_name IS '数据源名称';


--
-- Name: COLUMN metadata_datasource.ds_code; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.ds_code IS '数据源代码';


--
-- Name: COLUMN metadata_datasource.ds_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.ds_type IS '数据源类型';


--
-- Name: COLUMN metadata_datasource.ds_adapter; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.ds_adapter IS '数据源适配器';


--
-- Name: COLUMN metadata_datasource.ds_ip; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.ds_ip IS '数据源IP';


--
-- Name: COLUMN metadata_datasource.ds_port; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.ds_port IS '数据源端口';


--
-- Name: COLUMN metadata_datasource.jdbc_url; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.jdbc_url IS 'Jdbc连接url';


--
-- Name: COLUMN metadata_datasource.username; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.username IS '用户名';


--
-- Name: COLUMN metadata_datasource.password; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.password IS '密码（加密存储，AES-256，密钥走 sys_config）';


--
-- Name: COLUMN metadata_datasource.driver_class; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.driver_class IS '驱动类';


--
-- Name: COLUMN metadata_datasource.connection_props; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.connection_props IS '额外连接参数';


--
-- Name: COLUMN metadata_datasource.description; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.description IS '描述';


--
-- Name: COLUMN metadata_datasource.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.status IS '状态';


--
-- Name: COLUMN metadata_datasource.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.created_at IS '创建时间';


--
-- Name: COLUMN metadata_datasource.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.created_by IS '创建人';


--
-- Name: COLUMN metadata_datasource.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_datasource.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.updated_by IS '更新人';


--
-- Name: COLUMN metadata_datasource.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_datasource.deleted_at IS '删除时间';


--
-- Name: metadata_datasource_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_datasource_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_datasource_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_datasource_id_seq OWNED BY dam_dam.metadata_datasource.id;


--
-- Name: metadata_destruction_record; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_destruction_record (
    id bigint NOT NULL,
    destruction_no character varying(50),
    target_type character varying(50),
    target_id integer,
    target_name character varying(100),
    destruction_reason text,
    destruction_scope character varying(100),
    filter_condition text,
    affected_rows integer,
    destruction_method character varying(50),
    requester_id character varying(50),
    approver_id character varying(50),
    executor_id character varying(50),
    requested_at timestamp without time zone,
    approved_at timestamp without time zone,
    executed_at timestamp without time zone,
    remark character varying(500),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_destruction_record; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_destruction_record IS '数据销毁记录表—— 数据销毁记录（申请-审批-执行三段留痕）';


--
-- Name: COLUMN metadata_destruction_record.destruction_no; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.destruction_no IS '销毁单号';


--
-- Name: COLUMN metadata_destruction_record.target_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.target_type IS '目标类型';


--
-- Name: COLUMN metadata_destruction_record.target_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.target_id IS '目标ID';


--
-- Name: COLUMN metadata_destruction_record.target_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.target_name IS '目标名称';


--
-- Name: COLUMN metadata_destruction_record.destruction_reason; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.destruction_reason IS '销毁理由';


--
-- Name: COLUMN metadata_destruction_record.destruction_scope; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.destruction_scope IS '范围';


--
-- Name: COLUMN metadata_destruction_record.filter_condition; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.filter_condition IS '筛选条件';


--
-- Name: COLUMN metadata_destruction_record.affected_rows; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.affected_rows IS '影响行数';


--
-- Name: COLUMN metadata_destruction_record.destruction_method; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.destruction_method IS '销毁方法';


--
-- Name: COLUMN metadata_destruction_record.requester_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.requester_id IS '申请人ID';


--
-- Name: COLUMN metadata_destruction_record.approver_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.approver_id IS '审批人ID';


--
-- Name: COLUMN metadata_destruction_record.executor_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.executor_id IS '执行人ID';


--
-- Name: COLUMN metadata_destruction_record.requested_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.requested_at IS '申请时间';


--
-- Name: COLUMN metadata_destruction_record.approved_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.approved_at IS '审批时间';


--
-- Name: COLUMN metadata_destruction_record.executed_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.executed_at IS '执行时间';


--
-- Name: COLUMN metadata_destruction_record.remark; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.remark IS '备注';


--
-- Name: COLUMN metadata_destruction_record.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.status IS '状态';


--
-- Name: COLUMN metadata_destruction_record.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.created_at IS '创建时间';


--
-- Name: COLUMN metadata_destruction_record.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.created_by IS '创建人';


--
-- Name: COLUMN metadata_destruction_record.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_destruction_record.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.updated_by IS '更新人';


--
-- Name: COLUMN metadata_destruction_record.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_destruction_record.deleted_at IS '删除时间';


--
-- Name: metadata_destruction_record_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_destruction_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_destruction_record_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_destruction_record_id_seq OWNED BY dam_dam.metadata_destruction_record.id;


--
-- Name: metadata_lifecycle_policy; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_lifecycle_policy (
    id bigint NOT NULL,
    policy_code character varying(50),
    policy_name character varying(100),
    target_type character varying(50),
    target_id integer,
    hot_days integer,
    cold_days integer,
    archive_days integer,
    purge_days integer,
    cold_storage_location character varying(500),
    archive_storage_location character varying(500),
    archive_format character varying(50),
    auto_execute boolean,
    last_execute_time timestamp without time zone,
    description character varying(500),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_lifecycle_policy; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_lifecycle_policy IS '生命周期策略表—— 生命周期策略（热/冷/归档/清理四段天数+存储位置+格式）';


--
-- Name: COLUMN metadata_lifecycle_policy.policy_code; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.policy_code IS '策略代码';


--
-- Name: COLUMN metadata_lifecycle_policy.policy_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.policy_name IS '策略名';


--
-- Name: COLUMN metadata_lifecycle_policy.target_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.target_type IS '目标类型';


--
-- Name: COLUMN metadata_lifecycle_policy.target_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.target_id IS '目标ID';


--
-- Name: COLUMN metadata_lifecycle_policy.hot_days; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.hot_days IS '热数据保留天数';


--
-- Name: COLUMN metadata_lifecycle_policy.cold_days; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.cold_days IS '冷数据保留天数';


--
-- Name: COLUMN metadata_lifecycle_policy.archive_days; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.archive_days IS '归档触发天数';


--
-- Name: COLUMN metadata_lifecycle_policy.purge_days; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.purge_days IS '清理触发天数';


--
-- Name: COLUMN metadata_lifecycle_policy.cold_storage_location; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.cold_storage_location IS '冷存储路径';


--
-- Name: COLUMN metadata_lifecycle_policy.archive_storage_location; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.archive_storage_location IS '归档存储路径';


--
-- Name: COLUMN metadata_lifecycle_policy.archive_format; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.archive_format IS '归档格式';


--
-- Name: COLUMN metadata_lifecycle_policy.auto_execute; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.auto_execute IS '是否自动执行';


--
-- Name: COLUMN metadata_lifecycle_policy.last_execute_time; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.last_execute_time IS '最后执行时间';


--
-- Name: COLUMN metadata_lifecycle_policy.description; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.description IS '策略描述';


--
-- Name: COLUMN metadata_lifecycle_policy.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.status IS '状态';


--
-- Name: COLUMN metadata_lifecycle_policy.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.created_at IS '创建时间';


--
-- Name: COLUMN metadata_lifecycle_policy.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.created_by IS '创建人';


--
-- Name: COLUMN metadata_lifecycle_policy.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_lifecycle_policy.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.updated_by IS '更新人';


--
-- Name: COLUMN metadata_lifecycle_policy.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lifecycle_policy.deleted_at IS '删除时间';


--
-- Name: metadata_lifecycle_policy_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_lifecycle_policy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_lifecycle_policy_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_lifecycle_policy_id_seq OWNED BY dam_dam.metadata_lifecycle_policy.id;


--
-- Name: metadata_lineage; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_lineage (
    id bigint NOT NULL,
    source_type character varying(50) NOT NULL,
    source_id integer NOT NULL,
    target_type character varying(50) NOT NULL,
    target_id integer NOT NULL,
    relation_type character varying(50) NOT NULL,
    transform_rule text NOT NULL,
    dependency_level integer NOT NULL,
    source_system character varying(50) NOT NULL,
    collection_id integer NOT NULL,
    discovered_by character varying(100),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_lineage; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_lineage IS '数据血缘关系表—— 血缘关系（通用节点对：source/target 的 type+id，兼容表级/字段级/模型级）';


--
-- Name: COLUMN metadata_lineage.source_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.source_type IS '源节点类型';


--
-- Name: COLUMN metadata_lineage.source_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.source_id IS '源节点ID';


--
-- Name: COLUMN metadata_lineage.target_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.target_type IS '目标节点类型';


--
-- Name: COLUMN metadata_lineage.target_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.target_id IS '目标节点ID';


--
-- Name: COLUMN metadata_lineage.relation_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.relation_type IS '关系类型';


--
-- Name: COLUMN metadata_lineage.transform_rule; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.transform_rule IS '转换规则';


--
-- Name: COLUMN metadata_lineage.dependency_level; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.dependency_level IS '依赖层级';


--
-- Name: COLUMN metadata_lineage.source_system; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.source_system IS '来源系统标识';


--
-- Name: COLUMN metadata_lineage.collection_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.collection_id IS '关联采集记录ID';


--
-- Name: COLUMN metadata_lineage.discovered_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.discovered_by IS '发现方式';


--
-- Name: COLUMN metadata_lineage.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.status IS '状态';


--
-- Name: COLUMN metadata_lineage.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.created_at IS '创建时间';


--
-- Name: COLUMN metadata_lineage.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.created_by IS '创建人';


--
-- Name: COLUMN metadata_lineage.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_lineage.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.updated_by IS '更新人';


--
-- Name: COLUMN metadata_lineage.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_lineage.deleted_at IS '删除时间';


--
-- Name: metadata_lineage_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_lineage_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_lineage_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_lineage_id_seq OWNED BY dam_dam.metadata_lineage.id;


--
-- Name: metadata_management; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_management (
    id bigint NOT NULL,
    table_id bigint NOT NULL,
    governance_status_id character varying(30) NOT NULL,
    lifecycle_status_id character varying(20),
    retention_days bigint,
    source_system_id bigint NOT NULL,
    etl_workflow character varying(200),
    last_accessed_at timestamp without time zone,
    daily_query_avg bigint,
    security_classification character varying(20),
    data_verification_status character varying(20),
    registration_batch_id character varying(50),
    last_ddl_time timestamp without time zone,
    audit_remark text,
    extend_props jsonb,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_management; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_management IS '管理元数据表—— 管理元数据（治理状态/生命周期/保留策略/安全分级/使用热度 15 维）';


--
-- Name: COLUMN metadata_management.table_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.table_id IS '关联技术元数据表ID';


--
-- Name: COLUMN metadata_management.governance_status_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.governance_status_id IS '治理状态id';


--
-- Name: COLUMN metadata_management.lifecycle_status_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.lifecycle_status_id IS '生命周期状态id';


--
-- Name: COLUMN metadata_management.retention_days; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.retention_days IS '数据保留天数';


--
-- Name: COLUMN metadata_management.source_system_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.source_system_id IS '数据来源系统id';


--
-- Name: COLUMN metadata_management.etl_workflow; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.etl_workflow IS 'ETL/数据集成工作流名称';


--
-- Name: COLUMN metadata_management.last_accessed_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.last_accessed_at IS '最后被访问/查询的时间';


--
-- Name: COLUMN metadata_management.daily_query_avg; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.daily_query_avg IS '日均查询次数';


--
-- Name: COLUMN metadata_management.security_classification; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.security_classification IS '安全分级id';


--
-- Name: COLUMN metadata_management.data_verification_status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.data_verification_status IS '数据验证状态id';


--
-- Name: COLUMN metadata_management.registration_batch_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.registration_batch_id IS '注册批次ID';


--
-- Name: COLUMN metadata_management.last_ddl_time; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.last_ddl_time IS '表结构变更时间';


--
-- Name: COLUMN metadata_management.audit_remark; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.audit_remark IS '审计备注';


--
-- Name: COLUMN metadata_management.extend_props; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.extend_props IS '扩展属性';


--
-- Name: COLUMN metadata_management.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.status IS '状态';


--
-- Name: COLUMN metadata_management.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.created_at IS '创建时间';


--
-- Name: COLUMN metadata_management.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.created_by IS '创建人';


--
-- Name: COLUMN metadata_management.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_management.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.updated_by IS '更新人';


--
-- Name: COLUMN metadata_management.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_management.deleted_at IS '删除时间';


--
-- Name: metadata_management_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_management_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_management_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_management_id_seq OWNED BY dam_dam.metadata_management.id;


--
-- Name: metadata_quality_check_result; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_quality_check_result (
    id bigint NOT NULL,
    rule_id integer,
    check_time timestamp without time zone,
    total_count integer,
    pass_count integer,
    fail_count integer,
    pass_rate numeric(5,2),
    result_status character varying(50),
    fail_samples jsonb,
    error_detail text,
    executed_by character varying(100),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_quality_check_result; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_quality_check_result IS '质量检测结果表—— 质量检测结果（通过率/失败样例 JSONB/错误详情）';


--
-- Name: COLUMN metadata_quality_check_result.rule_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.rule_id IS '规则ID';


--
-- Name: COLUMN metadata_quality_check_result.check_time; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.check_time IS '检测时间';


--
-- Name: COLUMN metadata_quality_check_result.total_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.total_count IS '总记录数';


--
-- Name: COLUMN metadata_quality_check_result.pass_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.pass_count IS '通过记录数';


--
-- Name: COLUMN metadata_quality_check_result.fail_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.fail_count IS '不通过记录数';


--
-- Name: COLUMN metadata_quality_check_result.pass_rate; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.pass_rate IS '通过率';


--
-- Name: COLUMN metadata_quality_check_result.result_status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.result_status IS '结果状态';


--
-- Name: COLUMN metadata_quality_check_result.fail_samples; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.fail_samples IS '失败样例';


--
-- Name: COLUMN metadata_quality_check_result.error_detail; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.error_detail IS '错误详情';


--
-- Name: COLUMN metadata_quality_check_result.executed_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.executed_by IS '执行人';


--
-- Name: COLUMN metadata_quality_check_result.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.status IS '状态';


--
-- Name: COLUMN metadata_quality_check_result.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.created_at IS '创建时间';


--
-- Name: COLUMN metadata_quality_check_result.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.created_by IS '创建人';


--
-- Name: COLUMN metadata_quality_check_result.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_quality_check_result.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.updated_by IS '更新人';


--
-- Name: COLUMN metadata_quality_check_result.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_check_result.deleted_at IS '删除时间';


--
-- Name: metadata_quality_check_result_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_quality_check_result_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_quality_check_result_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_quality_check_result_id_seq OWNED BY dam_dam.metadata_quality_check_result.id;


--
-- Name: metadata_quality_rule; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_quality_rule (
    id bigint NOT NULL,
    rule_code character varying(50) NOT NULL,
    rule_type character varying(100) NOT NULL,
    rule_level character varying(50) NOT NULL,
    target_table_id integer NOT NULL,
    target_column character varying(100),
    rule_expression text,
    expect_value character varying(200),
    severity character varying(50),
    is_scheduled boolean,
    cron_expression character varying(100),
    description character varying(500),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_quality_rule; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_quality_rule IS '质量规则表—— 质量规则（类型/级别/表达式/期望值/严重度，含 cron 定时字段——定时执行由 PowerJob 驱动）';


--
-- Name: COLUMN metadata_quality_rule.rule_code; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.rule_code IS '规则编码';


--
-- Name: COLUMN metadata_quality_rule.rule_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.rule_type IS '规则名称';


--
-- Name: COLUMN metadata_quality_rule.rule_level; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.rule_level IS '级别';


--
-- Name: COLUMN metadata_quality_rule.target_table_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.target_table_id IS '目标表ID';


--
-- Name: COLUMN metadata_quality_rule.target_column; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.target_column IS '目标字段名（空为全表）';


--
-- Name: COLUMN metadata_quality_rule.rule_expression; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.rule_expression IS '规则表达式或自定义SQL';


--
-- Name: COLUMN metadata_quality_rule.expect_value; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.expect_value IS '期望值（如 > 0.95）';


--
-- Name: COLUMN metadata_quality_rule.severity; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.severity IS '严重程度';


--
-- Name: COLUMN metadata_quality_rule.is_scheduled; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.is_scheduled IS '是否定时执行';


--
-- Name: COLUMN metadata_quality_rule.cron_expression; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.cron_expression IS '定时表达式';


--
-- Name: COLUMN metadata_quality_rule.description; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.description IS '规则描述';


--
-- Name: COLUMN metadata_quality_rule.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.status IS '状态';


--
-- Name: COLUMN metadata_quality_rule.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.created_at IS '创建时间';


--
-- Name: COLUMN metadata_quality_rule.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.created_by IS '创建人';


--
-- Name: COLUMN metadata_quality_rule.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_quality_rule.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.updated_by IS '更新人';


--
-- Name: COLUMN metadata_quality_rule.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_quality_rule.deleted_at IS '删除时间';


--
-- Name: metadata_quality_rule_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_quality_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_quality_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_quality_rule_id_seq OWNED BY dam_dam.metadata_quality_rule.id;


--
-- Name: metadata_schema; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_schema (
    id bigint NOT NULL,
    datasource_id bigint NOT NULL,
    schema_name character varying(50),
    schema_comment character varying(500),
    schema_type character varying(50),
    schema_owner character varying(100),
    environment character varying(50),
    location character varying(500),
    properties character varying(200),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_schema; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_schema IS '技术元数据-模式—— 技术元数据-Schema 层（环境标识 PROD/TEST/DEV、物理存储路径）';


--
-- Name: COLUMN metadata_schema.datasource_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.datasource_id IS '所属数据源ID';


--
-- Name: COLUMN metadata_schema.schema_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.schema_name IS 'Schema名称';


--
-- Name: COLUMN metadata_schema.schema_comment; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.schema_comment IS 'Schema注释';


--
-- Name: COLUMN metadata_schema.schema_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.schema_type IS 'Schema类型（如：DATABASE、CATALOG、VIRTUAL）';


--
-- Name: COLUMN metadata_schema.schema_owner; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.schema_owner IS 'Schema负责人ID';


--
-- Name: COLUMN metadata_schema.environment; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.environment IS '环境标识：PROD-生产、TEST-测试、DEV-开发';


--
-- Name: COLUMN metadata_schema.location; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.location IS '物理存储路径（如HDFS路径、S3路径）';


--
-- Name: COLUMN metadata_schema.properties; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.properties IS '扩展属性（JSON格式，如字符集、排序规则等）';


--
-- Name: COLUMN metadata_schema.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.status IS '状态';


--
-- Name: COLUMN metadata_schema.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.created_at IS '创建时间';


--
-- Name: COLUMN metadata_schema.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.created_by IS '创建人';


--
-- Name: COLUMN metadata_schema.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_schema.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.updated_by IS '更新人';


--
-- Name: COLUMN metadata_schema.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_schema.deleted_at IS '删除时间';


--
-- Name: metadata_schema_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_schema_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_schema_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_schema_id_seq OWNED BY dam_dam.metadata_schema.id;


--
-- Name: metadata_table; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_table (
    id bigint NOT NULL,
    datasource_id bigint NOT NULL,
    schema_id bigint NOT NULL,
    table_name character varying(100) NOT NULL,
    table_comment character varying(500),
    table_type character varying(50) NOT NULL,
    row_count character varying(100),
    table_size character varying(100),
    created_time timestamp without time zone NOT NULL,
    last_modified timestamp without time zone NOT NULL,
    last_sync_time timestamp without time zone NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone,
    layer character varying(16)
);


--
-- Name: TABLE metadata_table; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_table IS '技术元数据-表—— 技术元数据-表级（行数/大小/DDL 时间/同步时间）';


--
-- Name: COLUMN metadata_table.datasource_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.datasource_id IS '所属数据源ID';


--
-- Name: COLUMN metadata_table.schema_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.schema_id IS 'Schema ID（关联 metadata_schema.id，类型统一）';


--
-- Name: COLUMN metadata_table.table_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.table_name IS '表名';


--
-- Name: COLUMN metadata_table.table_comment; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.table_comment IS '表注释';


--
-- Name: COLUMN metadata_table.table_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.table_type IS '表类型：TABLE/VIEW';


--
-- Name: COLUMN metadata_table.row_count; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.row_count IS '行数';


--
-- Name: COLUMN metadata_table.table_size; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.table_size IS '表大小（字节）';


--
-- Name: COLUMN metadata_table.created_time; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.created_time IS '表创建时间';


--
-- Name: COLUMN metadata_table.last_modified; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.last_modified IS '最后修改时间';


--
-- Name: COLUMN metadata_table.last_sync_time; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.last_sync_time IS '最后同步时间';


--
-- Name: COLUMN metadata_table.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.status IS '状态';


--
-- Name: COLUMN metadata_table.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.created_at IS '创建时间';


--
-- Name: COLUMN metadata_table.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.created_by IS '创建人';


--
-- Name: COLUMN metadata_table.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_table.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.updated_by IS '更新人';


--
-- Name: COLUMN metadata_table.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.deleted_at IS '删除时间';


--
-- Name: COLUMN metadata_table.layer; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_table.layer IS '数仓分层：SRC/ODS/DIM/DWD/DWS/ADS';


--
-- Name: metadata_table_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_table_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_table_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_table_id_seq OWNED BY dam_dam.metadata_table.id;


--
-- Name: metadata_version; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.metadata_version (
    id bigint NOT NULL,
    datasource_id integer NOT NULL,
    schema_id integer NOT NULL,
    table_id integer NOT NULL,
    version_number character varying(50) NOT NULL,
    change_type character varying(100) NOT NULL,
    change_summary text NOT NULL,
    snapshot_data jsonb NOT NULL,
    diff_summary jsonb,
    changed_by character varying(100) NOT NULL,
    changed_at timestamp without time zone NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE metadata_version; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.metadata_version IS '元数据版本表—— 元数据版本快照（JSONB 快照 + diff 摘要，支撑变更对比与通知）';


--
-- Name: COLUMN metadata_version.datasource_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.datasource_id IS '数据源ID';


--
-- Name: COLUMN metadata_version.schema_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.schema_id IS 'schemaID';


--
-- Name: COLUMN metadata_version.table_id; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.table_id IS '表ID';


--
-- Name: COLUMN metadata_version.version_number; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.version_number IS '版本号';


--
-- Name: COLUMN metadata_version.change_type; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.change_type IS '变更类型';


--
-- Name: COLUMN metadata_version.change_summary; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.change_summary IS '变更摘要';


--
-- Name: COLUMN metadata_version.snapshot_data; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.snapshot_data IS '版本快照';


--
-- Name: COLUMN metadata_version.diff_summary; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.diff_summary IS '差异摘要';


--
-- Name: COLUMN metadata_version.changed_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.changed_by IS '变更人';


--
-- Name: COLUMN metadata_version.changed_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.changed_at IS '变更时间';


--
-- Name: COLUMN metadata_version.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.status IS '状态';


--
-- Name: COLUMN metadata_version.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.created_at IS '创建时间';


--
-- Name: COLUMN metadata_version.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.created_by IS '创建人';


--
-- Name: COLUMN metadata_version.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.updated_at IS '更新时间';


--
-- Name: COLUMN metadata_version.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.updated_by IS '更新人';


--
-- Name: COLUMN metadata_version.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.metadata_version.deleted_at IS '删除时间';


--
-- Name: metadata_version_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.metadata_version_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: metadata_version_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.metadata_version_id_seq OWNED BY dam_dam.metadata_version.id;


--
-- Name: source_system; Type: TABLE; Schema: dam_dam; Owner: -
--

CREATE TABLE dam_dam.source_system (
    id bigint NOT NULL,
    system_code character varying(100) NOT NULL,
    system_name character varying(200) NOT NULL,
    description character varying(500),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE source_system; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON TABLE dam_dam.source_system IS '来源系统表—— 来源系统登记（HIS/EMR/LIS/IRIS…）';


--
-- Name: COLUMN source_system.system_code; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.source_system.system_code IS '系统代码';


--
-- Name: COLUMN source_system.system_name; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.source_system.system_name IS '系统名称';


--
-- Name: COLUMN source_system.description; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.source_system.description IS '描述';


--
-- Name: COLUMN source_system.status; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.source_system.status IS '状态';


--
-- Name: COLUMN source_system.created_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.source_system.created_at IS '创建时间';


--
-- Name: COLUMN source_system.created_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.source_system.created_by IS '创建人';


--
-- Name: COLUMN source_system.updated_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.source_system.updated_at IS '更新时间';


--
-- Name: COLUMN source_system.updated_by; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.source_system.updated_by IS '更新人';


--
-- Name: COLUMN source_system.deleted_at; Type: COMMENT; Schema: dam_dam; Owner: -
--

COMMENT ON COLUMN dam_dam.source_system.deleted_at IS '删除时间';


--
-- Name: source_system_id_seq; Type: SEQUENCE; Schema: dam_dam; Owner: -
--

CREATE SEQUENCE dam_dam.source_system_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: source_system_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dam; Owner: -
--

ALTER SEQUENCE dam_dam.source_system_id_seq OWNED BY dam_dam.source_system.id;


--
-- Name: report_catalog; Type: TABLE; Schema: dam_dan; Owner: -
--

CREATE TABLE dam_dan.report_catalog (
    id bigint NOT NULL,
    asset_id bigint NOT NULL,
    report_code character varying(128) NOT NULL,
    report_name character varying(255) NOT NULL,
    report_type_id character varying(50),
    dimension_codes jsonb,
    exec_type character varying(20) DEFAULT 'SQL'::character varying NOT NULL,
    data_source_id bigint,
    sql_content text,
    endpoint_url character varying(1000),
    param_defs jsonb,
    result_columns jsonb,
    cover_image character varying(500),
    update_frequency character varying(20),
    owner character varying(100),
    description character varying(1000),
    sort_order bigint DEFAULT 0 NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE report_catalog; Type: COMMENT; Schema: dam_dan; Owner: -
--

COMMENT ON TABLE dam_dan.report_catalog IS '报表资产扩展属性（1:1 asset_info，source_metadata_type=REPORT）';


--
-- Name: COLUMN report_catalog.dimension_codes; Type: COMMENT; Schema: dam_dan; Owner: -
--

COMMENT ON COLUMN dam_dan.report_catalog.dimension_codes IS '业务维度标签（jsonb 数组，如 ["科室","时间"]）';


--
-- Name: COLUMN report_catalog.exec_type; Type: COMMENT; Schema: dam_dan; Owner: -
--

COMMENT ON COLUMN dam_dan.report_catalog.exec_type IS '执行方式：SQL-普通查询 / PROC-存储过程 / HTTP-接口 / IFRAME-嵌入式页面 / STATIC-静态说明';


--
-- Name: COLUMN report_catalog.param_defs; Type: COMMENT; Schema: dam_dan; Owner: -
--

COMMENT ON COLUMN dam_dan.report_catalog.param_defs IS '参数定义（jsonb 数组，如 [{code,label,type,required}]）';


--
-- Name: report_catalog_id_seq; Type: SEQUENCE; Schema: dam_dan; Owner: -
--

CREATE SEQUENCE dam_dan.report_catalog_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: report_catalog_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dan; Owner: -
--

ALTER SEQUENCE dam_dan.report_catalog_id_seq OWNED BY dam_dan.report_catalog.id;


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
-- Name: report_dimension; Type: TABLE; Schema: dam_dimension; Owner: -
--

CREATE TABLE dam_dimension.report_dimension (
    id bigint NOT NULL,
    dim_code character varying(64) NOT NULL,
    dim_name character varying(100) NOT NULL,
    source_type character varying(20) DEFAULT 'STATIC'::character varying NOT NULL,
    source_ref character varying(100),
    sort_order bigint DEFAULT 0 NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE report_dimension; Type: COMMENT; Schema: dam_dimension; Owner: -
--

COMMENT ON TABLE dam_dimension.report_dimension IS '报表体系维度定义（CAD: SUBJECT_DOMAIN/REPORT_TYPE/UPDATE_FREQ，后期可扩展）';


--
-- Name: COLUMN report_dimension.source_type; Type: COMMENT; Schema: dam_dimension; Owner: -
--

COMMENT ON COLUMN dam_dimension.report_dimension.source_type IS '维度值来源：CATEGORY-资产主题域树 / DICT-字典 / STATIC-静态项';


--
-- Name: report_dimension_id_seq; Type: SEQUENCE; Schema: dam_dimension; Owner: -
--

CREATE SEQUENCE dam_dimension.report_dimension_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: report_dimension_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dimension; Owner: -
--

ALTER SEQUENCE dam_dimension.report_dimension_id_seq OWNED BY dam_dimension.report_dimension.id;


--
-- Name: report_dimension_item; Type: TABLE; Schema: dam_dimension; Owner: -
--

CREATE TABLE dam_dimension.report_dimension_item (
    id bigint NOT NULL,
    dimension_id bigint NOT NULL,
    item_code character varying(64) NOT NULL,
    item_name character varying(100) NOT NULL,
    sort_order bigint DEFAULT 0 NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by character varying(64) DEFAULT 'system'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE report_dimension_item; Type: COMMENT; Schema: dam_dimension; Owner: -
--

COMMENT ON TABLE dam_dimension.report_dimension_item IS '报表维度值（静态维度使用）';


--
-- Name: report_dimension_item_id_seq; Type: SEQUENCE; Schema: dam_dimension; Owner: -
--

CREATE SEQUENCE dam_dimension.report_dimension_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: report_dimension_item_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dimension; Owner: -
--

ALTER SEQUENCE dam_dimension.report_dimension_item_id_seq OWNED BY dam_dimension.report_dimension_item.id;


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
-- Name: model_column_mapping; Type: TABLE; Schema: dam_dpp; Owner: -
--

CREATE TABLE dam_dpp.model_column_mapping (
    id bigint NOT NULL,
    model_id integer,
    model_type character varying(50),
    source_table_id integer,
    source_column_id integer,
    target_column_name character varying(100),
    target_column_type character varying(50),
    is_primary_key integer,
    is_foreign_key integer,
    ref_dimension_id integer,
    transform_rule character varying(500),
    sort_order integer,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE model_column_mapping; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON TABLE dam_dpp.model_column_mapping IS '模型字段映射表—— 模型字段映射（模型字段↔技术元数据字段）';


--
-- Name: COLUMN model_column_mapping.model_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.model_id IS '模型ID';


--
-- Name: COLUMN model_column_mapping.model_type; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.model_type IS '模型类型：事实、维度';


--
-- Name: COLUMN model_column_mapping.source_table_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.source_table_id IS '源表ID';


--
-- Name: COLUMN model_column_mapping.source_column_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.source_column_id IS '源字段ID';


--
-- Name: COLUMN model_column_mapping.target_column_name; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.target_column_name IS '目标字段';


--
-- Name: COLUMN model_column_mapping.target_column_type; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.target_column_type IS '目标字段类型';


--
-- Name: COLUMN model_column_mapping.is_primary_key; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.is_primary_key IS '是否主键';


--
-- Name: COLUMN model_column_mapping.is_foreign_key; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.is_foreign_key IS '是否外键';


--
-- Name: COLUMN model_column_mapping.ref_dimension_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.ref_dimension_id IS '引用维度ID';


--
-- Name: COLUMN model_column_mapping.transform_rule; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.transform_rule IS '转换规则';


--
-- Name: COLUMN model_column_mapping.sort_order; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.sort_order IS '排序序号';


--
-- Name: COLUMN model_column_mapping.status; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.status IS '状态';


--
-- Name: COLUMN model_column_mapping.created_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.created_at IS '创建时间';


--
-- Name: COLUMN model_column_mapping.created_by; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.created_by IS '创建人';


--
-- Name: COLUMN model_column_mapping.updated_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.updated_at IS '更新时间';


--
-- Name: COLUMN model_column_mapping.updated_by; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.updated_by IS '更新人';


--
-- Name: COLUMN model_column_mapping.deleted_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_column_mapping.deleted_at IS '删除时间';


--
-- Name: model_column_mapping_id_seq; Type: SEQUENCE; Schema: dam_dpp; Owner: -
--

CREATE SEQUENCE dam_dpp.model_column_mapping_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_column_mapping_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpp; Owner: -
--

ALTER SEQUENCE dam_dpp.model_column_mapping_id_seq OWNED BY dam_dpp.model_column_mapping.id;


--
-- Name: model_dimension; Type: TABLE; Schema: dam_dpp; Owner: -
--

CREATE TABLE dam_dpp.model_dimension (
    id bigint NOT NULL,
    domain_id integer NOT NULL,
    dimension_code character varying(100) NOT NULL,
    dimension_name character varying(200) NOT NULL,
    dimension_type_id integer,
    source_table_id integer,
    description text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE model_dimension; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON TABLE dam_dpp.model_dimension IS '维度表—— 维度模型（挂主题域）';


--
-- Name: COLUMN model_dimension.domain_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.domain_id IS '所属主题域ID';


--
-- Name: COLUMN model_dimension.dimension_code; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.dimension_code IS '维度代码';


--
-- Name: COLUMN model_dimension.dimension_name; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.dimension_name IS '维度名称';


--
-- Name: COLUMN model_dimension.dimension_type_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.dimension_type_id IS '维度类型ID';


--
-- Name: COLUMN model_dimension.source_table_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.source_table_id IS '源表ID';


--
-- Name: COLUMN model_dimension.description; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.description IS '描述';


--
-- Name: COLUMN model_dimension.status; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.status IS '状态';


--
-- Name: COLUMN model_dimension.created_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.created_at IS '创建时间';


--
-- Name: COLUMN model_dimension.created_by; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.created_by IS '创建人';


--
-- Name: COLUMN model_dimension.updated_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.updated_at IS '更新时间';


--
-- Name: COLUMN model_dimension.updated_by; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.updated_by IS '更新人';


--
-- Name: COLUMN model_dimension.deleted_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_dimension.deleted_at IS '删除时间';


--
-- Name: model_dimension_id_seq; Type: SEQUENCE; Schema: dam_dpp; Owner: -
--

CREATE SEQUENCE dam_dpp.model_dimension_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_dimension_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpp; Owner: -
--

ALTER SEQUENCE dam_dpp.model_dimension_id_seq OWNED BY dam_dpp.model_dimension.id;


--
-- Name: model_domain; Type: TABLE; Schema: dam_dpp; Owner: -
--

CREATE TABLE dam_dpp.model_domain (
    id bigint NOT NULL,
    domain_code character varying(100) NOT NULL,
    domain_name character varying(200) NOT NULL,
    parent_id integer,
    description text,
    sort_order integer NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE model_domain; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON TABLE dam_dpp.model_domain IS '模型主题域表—— 模型主题域（数据中心→业务域两级树）';


--
-- Name: COLUMN model_domain.domain_code; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.domain_code IS '主题域代码';


--
-- Name: COLUMN model_domain.domain_name; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.domain_name IS '主题域名称';


--
-- Name: COLUMN model_domain.parent_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.parent_id IS '父主题域ID';


--
-- Name: COLUMN model_domain.description; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.description IS '描述';


--
-- Name: COLUMN model_domain.sort_order; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.sort_order IS '排序序号';


--
-- Name: COLUMN model_domain.status; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.status IS '状态';


--
-- Name: COLUMN model_domain.created_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.created_at IS '创建时间';


--
-- Name: COLUMN model_domain.created_by; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.created_by IS '创建人';


--
-- Name: COLUMN model_domain.updated_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.updated_at IS '更新时间';


--
-- Name: COLUMN model_domain.updated_by; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.updated_by IS '更新人';


--
-- Name: COLUMN model_domain.deleted_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_domain.deleted_at IS '删除时间';


--
-- Name: model_domain_id_seq; Type: SEQUENCE; Schema: dam_dpp; Owner: -
--

CREATE SEQUENCE dam_dpp.model_domain_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_domain_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpp; Owner: -
--

ALTER SEQUENCE dam_dpp.model_domain_id_seq OWNED BY dam_dpp.model_domain.id;


--
-- Name: model_fact; Type: TABLE; Schema: dam_dpp; Owner: -
--

CREATE TABLE dam_dpp.model_fact (
    id bigint NOT NULL,
    domain_id integer NOT NULL,
    fact_code character varying(100) NOT NULL,
    fact_name character varying(200) NOT NULL,
    source_table_id integer,
    granularity character varying(100),
    description text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE model_fact; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON TABLE dam_dpp.model_fact IS '事实表—— 事实模型（挂主题域）';


--
-- Name: COLUMN model_fact.domain_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.domain_id IS '所属主题域ID';


--
-- Name: COLUMN model_fact.fact_code; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.fact_code IS '事实代码';


--
-- Name: COLUMN model_fact.fact_name; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.fact_name IS '事实名称';


--
-- Name: COLUMN model_fact.source_table_id; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.source_table_id IS '源表ID';


--
-- Name: COLUMN model_fact.granularity; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.granularity IS '粒度描述';


--
-- Name: COLUMN model_fact.description; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.description IS '描述';


--
-- Name: COLUMN model_fact.status; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.status IS '状态';


--
-- Name: COLUMN model_fact.created_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.created_at IS '创建时间';


--
-- Name: COLUMN model_fact.created_by; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.created_by IS '创建人';


--
-- Name: COLUMN model_fact.updated_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.updated_at IS '更新时间';


--
-- Name: COLUMN model_fact.updated_by; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.updated_by IS '更新人';


--
-- Name: COLUMN model_fact.deleted_at; Type: COMMENT; Schema: dam_dpp; Owner: -
--

COMMENT ON COLUMN dam_dpp.model_fact.deleted_at IS '删除时间';


--
-- Name: model_fact_id_seq; Type: SEQUENCE; Schema: dam_dpp; Owner: -
--

CREATE SEQUENCE dam_dpp.model_fact_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_fact_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_dpp; Owner: -
--

ALTER SEQUENCE dam_dpp.model_fact_id_seq OWNED BY dam_dpp.model_fact.id;


--
-- Name: api_call_log; Type: TABLE; Schema: dam_service; Owner: -
--

CREATE TABLE dam_service.api_call_log (
    id bigint NOT NULL,
    api_id bigint,
    api_code character varying(64),
    app_id character varying(64),
    params text,
    duration_ms integer,
    row_count integer,
    success boolean DEFAULT true NOT NULL,
    masked_columns character varying(255),
    error_msg text,
    client_ip character varying(64),
    called_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL
);


--
-- Name: TABLE api_call_log; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON TABLE dam_service.api_call_log IS '网关调用日志表—— 网关调用全量留痕（含脱敏字段记录；日志表不做软删）';


--
-- Name: COLUMN api_call_log.api_id; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.api_id IS 'API ID';


--
-- Name: COLUMN api_call_log.api_code; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.api_code IS 'API 编码';


--
-- Name: COLUMN api_call_log.app_id; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.app_id IS '调用方应用标识';


--
-- Name: COLUMN api_call_log.params; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.params IS '入参快照';


--
-- Name: COLUMN api_call_log.duration_ms; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.duration_ms IS '耗时（毫秒）';


--
-- Name: COLUMN api_call_log.row_count; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.row_count IS '返回行数';


--
-- Name: COLUMN api_call_log.success; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.success IS '是否成功';


--
-- Name: COLUMN api_call_log.masked_columns; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.masked_columns IS '本次被脱敏的字段清单';


--
-- Name: COLUMN api_call_log.error_msg; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.error_msg IS '错误信息';


--
-- Name: COLUMN api_call_log.client_ip; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.client_ip IS '调用方 IP';


--
-- Name: COLUMN api_call_log.called_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.called_at IS '调用时间';


--
-- Name: COLUMN api_call_log.status; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN api_call_log.created_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.created_at IS '创建时间';


--
-- Name: COLUMN api_call_log.created_by; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.created_by IS '创建人';


--
-- Name: COLUMN api_call_log.updated_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.updated_at IS '更新时间';


--
-- Name: COLUMN api_call_log.updated_by; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_call_log.updated_by IS '更新人';


--
-- Name: api_call_log_id_seq; Type: SEQUENCE; Schema: dam_service; Owner: -
--

CREATE SEQUENCE dam_service.api_call_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_call_log_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_service; Owner: -
--

ALTER SEQUENCE dam_service.api_call_log_id_seq OWNED BY dam_service.api_call_log.id;


--
-- Name: api_client; Type: TABLE; Schema: dam_service; Owner: -
--

CREATE TABLE dam_service.api_client (
    id bigint NOT NULL,
    app_id character varying(64) NOT NULL,
    app_key character varying(128) NOT NULL,
    app_name character varying(128),
    contact character varying(64),
    quota_day integer DEFAULT 50000 NOT NULL,
    last_call_at timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE api_client; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON TABLE dam_service.api_client IS '网关接入应用与密钥表—— 网关接入应用与密钥（保留并完善：应用级配额）';


--
-- Name: COLUMN api_client.app_id; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.app_id IS '应用标识（唯一，网关请求头 X-App-Id）';


--
-- Name: COLUMN api_client.app_key; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.app_key IS '应用密钥（网关请求头 X-App-Key，可轮换）';


--
-- Name: COLUMN api_client.app_name; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.app_name IS '应用名称';


--
-- Name: COLUMN api_client.contact; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.contact IS '联系人';


--
-- Name: COLUMN api_client.quota_day; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.quota_day IS '应用级日配额（完善项）';


--
-- Name: COLUMN api_client.last_call_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.last_call_at IS '最后调用时间';


--
-- Name: COLUMN api_client.status; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN api_client.created_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.created_at IS '创建时间';


--
-- Name: COLUMN api_client.created_by; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.created_by IS '创建人';


--
-- Name: COLUMN api_client.updated_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.updated_at IS '更新时间';


--
-- Name: COLUMN api_client.updated_by; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.updated_by IS '更新人';


--
-- Name: COLUMN api_client.deleted_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_client.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: api_client_id_seq; Type: SEQUENCE; Schema: dam_service; Owner: -
--

CREATE SEQUENCE dam_service.api_client_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_client_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_service; Owner: -
--

ALTER SEQUENCE dam_service.api_client_id_seq OWNED BY dam_service.api_client.id;


--
-- Name: api_service; Type: TABLE; Schema: dam_service; Owner: -
--

CREATE TABLE dam_service.api_service (
    id bigint NOT NULL,
    api_code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    asset_id bigint,
    sql_text text NOT NULL,
    params_json text,
    approval_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    rate_limit integer DEFAULT 60 NOT NULL,
    quota_day integer DEFAULT 10000 NOT NULL,
    owner character varying(64),
    description text,
    call_count bigint DEFAULT 0 NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE api_service; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON TABLE dam_service.api_service IS '网关 API 定义表—— 网关 API 定义（保留并完善：新增审批流/日配额）';


--
-- Name: COLUMN api_service.api_code; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.api_code IS 'API编码（唯一，URL 路径参数）';


--
-- Name: COLUMN api_service.name; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.name IS 'API名称';


--
-- Name: COLUMN api_service.asset_id; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.asset_id IS '关联资产ID（asset_info.id）';


--
-- Name: COLUMN api_service.sql_text; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.sql_text IS '查询 SQL（网关仅允许单条 SELECT，参数化执行）';


--
-- Name: COLUMN api_service.params_json; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.params_json IS '参数白名单定义（JSON 数组：name/type/required/desc/example）';


--
-- Name: COLUMN api_service.approval_status; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.approval_status IS '发布审批状态：PENDING/APPROVED/REJECTED（完善项）';


--
-- Name: COLUMN api_service.approved_by; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.approved_by IS '审批人（完善项）';


--
-- Name: COLUMN api_service.approved_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.approved_at IS '审批时间（完善项）';


--
-- Name: COLUMN api_service.rate_limit; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.rate_limit IS '每分钟调用上限（完善项：超限返回 429）';


--
-- Name: COLUMN api_service.quota_day; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.quota_day IS '单日调用配额（完善项）';


--
-- Name: COLUMN api_service.owner; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.owner IS 'API 负责人';


--
-- Name: COLUMN api_service.description; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.description IS '接口说明';


--
-- Name: COLUMN api_service.call_count; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.call_count IS '累计调用次数';


--
-- Name: COLUMN api_service.status; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN api_service.created_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.created_at IS '创建时间';


--
-- Name: COLUMN api_service.created_by; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.created_by IS '创建人';


--
-- Name: COLUMN api_service.updated_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.updated_at IS '更新时间';


--
-- Name: COLUMN api_service.updated_by; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.updated_by IS '更新人';


--
-- Name: COLUMN api_service.deleted_at; Type: COMMENT; Schema: dam_service; Owner: -
--

COMMENT ON COLUMN dam_service.api_service.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: api_service_id_seq; Type: SEQUENCE; Schema: dam_service; Owner: -
--

CREATE SEQUENCE dam_service.api_service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_service_id_seq; Type: SEQUENCE OWNED BY; Schema: dam_service; Owner: -
--

ALTER SEQUENCE dam_service.api_service_id_seq OWNED BY dam_service.api_service.id;


--
-- Name: dim_code; Type: TABLE; Schema: dim; Owner: -
--

CREATE TABLE dim.dim_code (
    id bigint NOT NULL,
    layer_domain character varying(64) NOT NULL,
    domain_name character varying(64) NOT NULL,
    code_table_code character varying(64) NOT NULL,
    code_table_name character varying(128),
    code_value character varying(64) NOT NULL,
    code_name character varying(128),
    sort_order integer,
    etl_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE dim_code; Type: COMMENT; Schema: dim; Owner: -
--

COMMENT ON TABLE dim.dim_code IS 'DIM 维度层通用码表：平台标准码表按思维导图 14 个 DIM_* 维度域归类';


--
-- Name: dim_code_id_seq; Type: SEQUENCE; Schema: dim; Owner: -
--

CREATE SEQUENCE dim.dim_code_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dim_code_id_seq; Type: SEQUENCE OWNED BY; Schema: dim; Owner: -
--

ALTER SEQUENCE dim.dim_code_id_seq OWNED BY dim.dim_code.id;


--
-- Name: dim_department; Type: TABLE; Schema: dim; Owner: -
--

CREATE TABLE dim.dim_department (
    dept_code character varying(64) NOT NULL,
    dept_name character varying(128) NOT NULL,
    dept_type character varying(32),
    etl_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE dim_department; Type: COMMENT; Schema: dim; Owner: -
--

COMMENT ON TABLE dim.dim_department IS 'DIM 科室维度（来源：平台标准码表 CT_Loc，域：组织架构与人员）';


--
-- Name: dim_diagnosis_icd10; Type: TABLE; Schema: dim; Owner: -
--

CREATE TABLE dim.dim_diagnosis_icd10 (
    icd_code character varying(32) NOT NULL,
    icd_name character varying(256) NOT NULL,
    etl_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE dim_diagnosis_icd10; Type: COMMENT; Schema: dim; Owner: -
--

COMMENT ON TABLE dim.dim_diagnosis_icd10 IS 'DIM 诊断维度 ICD-10（来源：CT_ICD10，域：诊断）';


--
-- Name: dim_surgery_icd9cm3; Type: TABLE; Schema: dim; Owner: -
--

CREATE TABLE dim.dim_surgery_icd9cm3 (
    surg_code character varying(32) NOT NULL,
    surg_name character varying(256) NOT NULL,
    etl_time timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE dim_surgery_icd9cm3; Type: COMMENT; Schema: dim; Owner: -
--

COMMENT ON TABLE dim.dim_surgery_icd9cm3 IS 'DIM 手术操作维度 ICD-9-CM-3（来源：CT_ICD9CM3，域：手术与治疗）';


--
-- Name: dwd_clinical_order; Type: TABLE; Schema: dwd; Owner: -
--

CREATE TABLE dwd.dwd_clinical_order (
    ordid character varying(64) NOT NULL,
    admid character varying(64),
    patid character varying(64),
    order_type character varying(16),
    order_type_name character varying(32),
    item_code character varying(64),
    item_name character varying(256),
    spec character varying(128),
    dosage character varying(64),
    frequency character varying(32),
    frequency_name character varying(64),
    route character varying(32),
    route_name character varying(64),
    doctor_name character varying(128),
    order_date timestamp without time zone,
    order_status character varying(16),
    etl_time timestamp without time zone DEFAULT now() NOT NULL,
    etl_batch character varying(64)
);


--
-- Name: TABLE dwd_clinical_order; Type: COMMENT; Schema: dwd; Owner: -
--

COMMENT ON TABLE dwd.dwd_clinical_order IS 'DWD 临床医嘱（域：医嘱管理，来源 ODS.OE_Order 清洗 + 频次/途径维度打标）';


--
-- Name: dwd_encounter; Type: TABLE; Schema: dwd; Owner: -
--

CREATE TABLE dwd.dwd_encounter (
    admid character varying(64) NOT NULL,
    patid character varying(64) NOT NULL,
    pat_name character varying(128),
    encounter_type character varying(16),
    encounter_name character varying(32),
    dept_code character varying(64),
    dept_name character varying(128),
    doctor_id character varying(64),
    doctor_name character varying(128),
    adm_date timestamp without time zone,
    disch_date timestamp without time zone,
    stay_days integer,
    encounter_status character varying(32),
    total_cost numeric(18,2),
    etl_time timestamp without time zone DEFAULT now() NOT NULL,
    etl_batch character varying(64)
);


--
-- Name: TABLE dwd_encounter; Type: COMMENT; Schema: dwd; Owner: -
--

COMMENT ON TABLE dwd.dwd_encounter IS 'DWD 就诊事件（域：诊疗活动管理，来源 ODS.PA_Adm 清洗 + 科室/就诊类型维度打标）';


--
-- Name: dwd_patient_master; Type: TABLE; Schema: dwd; Owner: -
--

CREATE TABLE dwd.dwd_patient_master (
    patid character varying(64) NOT NULL,
    pat_name character varying(128),
    sex_code character varying(16),
    sex_name character varying(32),
    birth_date date,
    id_no character varying(64),
    marital_code character varying(16),
    marital_name character varying(32),
    occupation character varying(64),
    reg_date timestamp without time zone,
    src_system character varying(32) DEFAULT 'HIS'::character varying NOT NULL,
    etl_time timestamp without time zone DEFAULT now() NOT NULL,
    etl_batch character varying(64)
);


--
-- Name: TABLE dwd_patient_master; Type: COMMENT; Schema: dwd; Owner: -
--

COMMENT ON TABLE dwd.dwd_patient_master IS 'DWD 患者主索引（域：主索引与患者管理，来源 ODS.PA_PatMas 清洗）';


--
-- Name: biz_aux_exam; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_aux_exam (
    id bigint NOT NULL,
    exam_no character varying(32) NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    exam_type character varying(16) NOT NULL,
    exam_date date,
    finding text,
    conclusion text,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_aux_exam; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_aux_exam IS '辅助检查表—— 辅助检查（心电/脑电/肺功能）';


--
-- Name: COLUMN biz_aux_exam.exam_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.exam_no IS '检查号（唯一）';


--
-- Name: COLUMN biz_aux_exam.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_aux_exam.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_aux_exam.exam_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.exam_type IS '类型：心电/脑电/肺功能/动态血压';


--
-- Name: COLUMN biz_aux_exam.exam_date; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.exam_date IS '检查日期';


--
-- Name: COLUMN biz_aux_exam.finding; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.finding IS '检查所见';


--
-- Name: COLUMN biz_aux_exam.conclusion; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.conclusion IS '检查结论';


--
-- Name: COLUMN biz_aux_exam.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_aux_exam.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.created_at IS '创建时间';


--
-- Name: COLUMN biz_aux_exam.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.created_by IS '创建人';


--
-- Name: COLUMN biz_aux_exam.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.updated_at IS '更新时间';


--
-- Name: COLUMN biz_aux_exam.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.updated_by IS '更新人';


--
-- Name: COLUMN biz_aux_exam.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_aux_exam.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_aux_exam_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_aux_exam_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_aux_exam_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_aux_exam_id_seq OWNED BY hospital_demo.biz_aux_exam.id;


--
-- Name: biz_clinical_order; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_clinical_order (
    id bigint NOT NULL,
    order_no character varying(32) NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    order_type character varying(8) NOT NULL,
    order_item_code character varying(32),
    order_item_name character varying(200) NOT NULL,
    spec character varying(100),
    dosage character varying(100),
    frequency character varying(32),
    route character varying(32),
    order_doctor character varying(64),
    order_time timestamp without time zone,
    order_status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_clinical_order; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_clinical_order IS '医嘱信息表—— 医嘱（长期/临时，FHIR ServiceRequest）';


--
-- Name: COLUMN biz_clinical_order.order_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.order_no IS '医嘱号（唯一）';


--
-- Name: COLUMN biz_clinical_order.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_clinical_order.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_clinical_order.order_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.order_type IS '医嘱类型：长期/临时';


--
-- Name: COLUMN biz_clinical_order.order_item_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.order_item_code IS '医嘱项目编码';


--
-- Name: COLUMN biz_clinical_order.order_item_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.order_item_name IS '医嘱项目名称';


--
-- Name: COLUMN biz_clinical_order.spec; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.spec IS '规格';


--
-- Name: COLUMN biz_clinical_order.dosage; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.dosage IS '单次用量';


--
-- Name: COLUMN biz_clinical_order.frequency; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.frequency IS '执行频次';


--
-- Name: COLUMN biz_clinical_order.route; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.route IS '给药途径';


--
-- Name: COLUMN biz_clinical_order.order_doctor; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.order_doctor IS '开立医生';


--
-- Name: COLUMN biz_clinical_order.order_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.order_time IS '开立时间';


--
-- Name: COLUMN biz_clinical_order.order_status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.order_status IS '状态：ACTIVE/STOPPED/DONE';


--
-- Name: COLUMN biz_clinical_order.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_clinical_order.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.created_at IS '创建时间';


--
-- Name: COLUMN biz_clinical_order.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.created_by IS '创建人';


--
-- Name: COLUMN biz_clinical_order.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.updated_at IS '更新时间';


--
-- Name: COLUMN biz_clinical_order.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.updated_by IS '更新人';


--
-- Name: COLUMN biz_clinical_order.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_clinical_order.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_clinical_order_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_clinical_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_clinical_order_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_clinical_order_id_seq OWNED BY hospital_demo.biz_clinical_order.id;


--
-- Name: biz_diagnosis; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_diagnosis (
    id bigint NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    diagnosis_icd character varying(20) NOT NULL,
    diagnosis_name character varying(200) NOT NULL,
    diagnosis_class character varying(8),
    diagnosis_type character varying(16),
    is_primary boolean DEFAULT false NOT NULL,
    doctor_code character varying(32),
    diagnosis_time timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_diagnosis; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_diagnosis IS '诊断记录表—— 诊断记录（FHIR Condition）';


--
-- Name: COLUMN biz_diagnosis.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_diagnosis.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_diagnosis.diagnosis_icd; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.diagnosis_icd IS '诊断 ICD 编码（ICD-10 / 中医病证）';


--
-- Name: COLUMN biz_diagnosis.diagnosis_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.diagnosis_name IS '诊断名称';


--
-- Name: COLUMN biz_diagnosis.diagnosis_class; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.diagnosis_class IS '分类：西医/中医';


--
-- Name: COLUMN biz_diagnosis.diagnosis_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.diagnosis_type IS '类型：入院/术前/出院诊断';


--
-- Name: COLUMN biz_diagnosis.is_primary; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.is_primary IS '是否主要诊断';


--
-- Name: COLUMN biz_diagnosis.doctor_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.doctor_code IS '诊断医生';


--
-- Name: COLUMN biz_diagnosis.diagnosis_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.diagnosis_time IS '诊断时间';


--
-- Name: COLUMN biz_diagnosis.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_diagnosis.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.created_at IS '创建时间';


--
-- Name: COLUMN biz_diagnosis.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.created_by IS '创建人';


--
-- Name: COLUMN biz_diagnosis.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.updated_at IS '更新时间';


--
-- Name: COLUMN biz_diagnosis.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.updated_by IS '更新人';


--
-- Name: COLUMN biz_diagnosis.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_diagnosis.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_diagnosis_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_diagnosis_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_diagnosis_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_diagnosis_id_seq OWNED BY hospital_demo.biz_diagnosis.id;


--
-- Name: biz_emr_document; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_emr_document (
    id bigint NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    doc_type character varying(32) NOT NULL,
    doc_title character varying(200),
    doc_content text,
    author_code character varying(32),
    author_name character varying(64),
    archive_time timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_emr_document; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_emr_document IS '电子病历文书表—— 电子病历文书（FHIR Composition）';


--
-- Name: COLUMN biz_emr_document.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_emr_document.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_emr_document.doc_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.doc_type IS '文书类型：入院记录/病程记录/出院小结等';


--
-- Name: COLUMN biz_emr_document.doc_title; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.doc_title IS '文书标题';


--
-- Name: COLUMN biz_emr_document.doc_content; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.doc_content IS '文书内容（结构化归档）';


--
-- Name: COLUMN biz_emr_document.author_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.author_code IS '书写医生编码';


--
-- Name: COLUMN biz_emr_document.author_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.author_name IS '书写医生姓名';


--
-- Name: COLUMN biz_emr_document.archive_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.archive_time IS '归档时间';


--
-- Name: COLUMN biz_emr_document.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_emr_document.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.created_at IS '创建时间';


--
-- Name: COLUMN biz_emr_document.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.created_by IS '创建人';


--
-- Name: COLUMN biz_emr_document.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.updated_at IS '更新时间';


--
-- Name: COLUMN biz_emr_document.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.updated_by IS '更新人';


--
-- Name: COLUMN biz_emr_document.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_emr_document.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_emr_document_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_emr_document_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_emr_document_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_emr_document_id_seq OWNED BY hospital_demo.biz_emr_document.id;


--
-- Name: biz_encounter; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_encounter (
    id bigint NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    encounter_type character varying(8) NOT NULL,
    dept_code character varying(16),
    dept_name character varying(64),
    doctor_code character varying(32),
    doctor_name character varying(64),
    admit_time timestamp without time zone,
    discharge_time timestamp without time zone,
    encounter_status character varying(8) DEFAULT 'OPEN'::character varying NOT NULL,
    total_cost numeric(12,2),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_encounter; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_encounter IS '就诊事件表—— 就诊事件（门诊/急诊/住院，FHIR Encounter）';


--
-- Name: COLUMN biz_encounter.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.encounter_no IS '就诊号（唯一）';


--
-- Name: COLUMN biz_encounter.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_encounter.encounter_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.encounter_type IS '就诊类型：门诊/急诊/住院';


--
-- Name: COLUMN biz_encounter.dept_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.dept_code IS '科室编码（码表 CT_Loc）';


--
-- Name: COLUMN biz_encounter.dept_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.dept_name IS '科室名称';


--
-- Name: COLUMN biz_encounter.doctor_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.doctor_code IS '医生编码（CT_CareProv）';


--
-- Name: COLUMN biz_encounter.doctor_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.doctor_name IS '医生姓名';


--
-- Name: COLUMN biz_encounter.admit_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.admit_time IS '就诊/入院时间';


--
-- Name: COLUMN biz_encounter.discharge_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.discharge_time IS '出院时间（住院）';


--
-- Name: COLUMN biz_encounter.encounter_status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.encounter_status IS '状态：OPEN/CLOSED';


--
-- Name: COLUMN biz_encounter.total_cost; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.total_cost IS '总费用';


--
-- Name: COLUMN biz_encounter.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_encounter.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.created_at IS '创建时间';


--
-- Name: COLUMN biz_encounter.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.created_by IS '创建人';


--
-- Name: COLUMN biz_encounter.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.updated_at IS '更新时间';


--
-- Name: COLUMN biz_encounter.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.updated_by IS '更新人';


--
-- Name: COLUMN biz_encounter.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_encounter_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_encounter_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_encounter_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_encounter_id_seq OWNED BY hospital_demo.biz_encounter.id;


--
-- Name: biz_encounter_transfer; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_encounter_transfer (
    id bigint NOT NULL,
    encounter_no character varying(32) NOT NULL,
    from_dept character varying(16),
    to_dept character varying(16),
    from_bed character varying(16),
    to_bed character varying(16),
    transfer_time timestamp without time zone NOT NULL,
    reason character varying(200),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_encounter_transfer; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_encounter_transfer IS '转科换床记录表—— 转科换床记录';


--
-- Name: COLUMN biz_encounter_transfer.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_encounter_transfer.from_dept; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.from_dept IS '转出科室';


--
-- Name: COLUMN biz_encounter_transfer.to_dept; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.to_dept IS '转入科室';


--
-- Name: COLUMN biz_encounter_transfer.from_bed; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.from_bed IS '原床位';


--
-- Name: COLUMN biz_encounter_transfer.to_bed; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.to_bed IS '新床位';


--
-- Name: COLUMN biz_encounter_transfer.transfer_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.transfer_time IS '转科/换床时间';


--
-- Name: COLUMN biz_encounter_transfer.reason; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.reason IS '转科原因';


--
-- Name: COLUMN biz_encounter_transfer.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_encounter_transfer.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.created_at IS '创建时间';


--
-- Name: COLUMN biz_encounter_transfer.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.created_by IS '创建人';


--
-- Name: COLUMN biz_encounter_transfer.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.updated_at IS '更新时间';


--
-- Name: COLUMN biz_encounter_transfer.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.updated_by IS '更新人';


--
-- Name: COLUMN biz_encounter_transfer.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_encounter_transfer.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_encounter_transfer_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_encounter_transfer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_encounter_transfer_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_encounter_transfer_id_seq OWNED BY hospital_demo.biz_encounter_transfer.id;


--
-- Name: biz_imaging_exam; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_imaging_exam (
    id bigint NOT NULL,
    exam_no character varying(32) NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    exam_type character varying(16) NOT NULL,
    body_part character varying(64),
    finding text,
    conclusion text,
    apply_doctor character varying(64),
    report_doctor character varying(64),
    report_time timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_imaging_exam; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_imaging_exam IS '影像检查表—— 影像/超声/内镜检查（FHIR ImagingStudy）';


--
-- Name: COLUMN biz_imaging_exam.exam_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.exam_no IS '检查号（唯一）';


--
-- Name: COLUMN biz_imaging_exam.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_imaging_exam.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_imaging_exam.exam_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.exam_type IS '检查类型：CT/MRI/X线/超声/内镜';


--
-- Name: COLUMN biz_imaging_exam.body_part; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.body_part IS '检查部位';


--
-- Name: COLUMN biz_imaging_exam.finding; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.finding IS '影像所见';


--
-- Name: COLUMN biz_imaging_exam.conclusion; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.conclusion IS '影像结论';


--
-- Name: COLUMN biz_imaging_exam.apply_doctor; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.apply_doctor IS '申请医生';


--
-- Name: COLUMN biz_imaging_exam.report_doctor; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.report_doctor IS '报告医生';


--
-- Name: COLUMN biz_imaging_exam.report_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.report_time IS '报告时间';


--
-- Name: COLUMN biz_imaging_exam.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_imaging_exam.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.created_at IS '创建时间';


--
-- Name: COLUMN biz_imaging_exam.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.created_by IS '创建人';


--
-- Name: COLUMN biz_imaging_exam.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.updated_at IS '更新时间';


--
-- Name: COLUMN biz_imaging_exam.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.updated_by IS '更新人';


--
-- Name: COLUMN biz_imaging_exam.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_imaging_exam.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_imaging_exam_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_imaging_exam_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_imaging_exam_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_imaging_exam_id_seq OWNED BY hospital_demo.biz_imaging_exam.id;


--
-- Name: biz_lab_report; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_lab_report (
    id bigint NOT NULL,
    report_no character varying(32) NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    lab_item_code character varying(32),
    lab_item_name character varying(200) NOT NULL,
    specimen character varying(64),
    apply_doctor character varying(64),
    report_doctor character varying(64),
    apply_time timestamp without time zone,
    report_time timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_lab_report; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_lab_report IS '检验报告表—— 检验报告（FHIR DiagnosticReport）';


--
-- Name: COLUMN biz_lab_report.report_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.report_no IS '检验报告号（唯一）';


--
-- Name: COLUMN biz_lab_report.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_lab_report.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_lab_report.lab_item_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.lab_item_code IS '检验申请项目编码';


--
-- Name: COLUMN biz_lab_report.lab_item_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.lab_item_name IS '检验项目名称';


--
-- Name: COLUMN biz_lab_report.specimen; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.specimen IS '标本类型：血/尿/便等';


--
-- Name: COLUMN biz_lab_report.apply_doctor; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.apply_doctor IS '申请医生';


--
-- Name: COLUMN biz_lab_report.report_doctor; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.report_doctor IS '报告医生';


--
-- Name: COLUMN biz_lab_report.apply_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.apply_time IS '申请时间';


--
-- Name: COLUMN biz_lab_report.report_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.report_time IS '报告时间';


--
-- Name: COLUMN biz_lab_report.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_lab_report.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.created_at IS '创建时间';


--
-- Name: COLUMN biz_lab_report.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.created_by IS '创建人';


--
-- Name: COLUMN biz_lab_report.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.updated_at IS '更新时间';


--
-- Name: COLUMN biz_lab_report.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.updated_by IS '更新人';


--
-- Name: COLUMN biz_lab_report.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_report.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_lab_report_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_lab_report_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_lab_report_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_lab_report_id_seq OWNED BY hospital_demo.biz_lab_report.id;


--
-- Name: biz_lab_result; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_lab_result (
    id bigint NOT NULL,
    report_no character varying(32) NOT NULL,
    item_code character varying(32) NOT NULL,
    item_name character varying(200) NOT NULL,
    result_value character varying(100),
    result_value_num numeric(18,4),
    unit character varying(32),
    ref_range character varying(64),
    abnormal_flag character varying(8),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_lab_result; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_lab_result IS '检验结果表—— 检验结果子项（FHIR Observation）';


--
-- Name: COLUMN biz_lab_result.report_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.report_no IS '检验报告号';


--
-- Name: COLUMN biz_lab_result.item_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.item_code IS '子项编码';


--
-- Name: COLUMN biz_lab_result.item_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.item_name IS '子项名称';


--
-- Name: COLUMN biz_lab_result.result_value; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.result_value IS '定性结果';


--
-- Name: COLUMN biz_lab_result.result_value_num; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.result_value_num IS '定量结果';


--
-- Name: COLUMN biz_lab_result.unit; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.unit IS '单位';


--
-- Name: COLUMN biz_lab_result.ref_range; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.ref_range IS '参考范围';


--
-- Name: COLUMN biz_lab_result.abnormal_flag; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.abnormal_flag IS '异常标志：H/L/正常';


--
-- Name: COLUMN biz_lab_result.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_lab_result.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.created_at IS '创建时间';


--
-- Name: COLUMN biz_lab_result.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.created_by IS '创建人';


--
-- Name: COLUMN biz_lab_result.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.updated_at IS '更新时间';


--
-- Name: COLUMN biz_lab_result.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.updated_by IS '更新人';


--
-- Name: COLUMN biz_lab_result.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_lab_result.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_lab_result_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_lab_result_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_lab_result_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_lab_result_id_seq OWNED BY hospital_demo.biz_lab_result.id;


--
-- Name: biz_order_exec; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_order_exec (
    id bigint NOT NULL,
    order_no character varying(32) NOT NULL,
    exec_time timestamp without time zone NOT NULL,
    exec_nurse character varying(64),
    exec_dept character varying(16),
    exec_status character varying(8) DEFAULT 'DONE'::character varying NOT NULL,
    exec_note character varying(200),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_order_exec; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_order_exec IS '医嘱执行记录表—— 医嘱执行记录';


--
-- Name: COLUMN biz_order_exec.order_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.order_no IS '医嘱号';


--
-- Name: COLUMN biz_order_exec.exec_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.exec_time IS '执行时间';


--
-- Name: COLUMN biz_order_exec.exec_nurse; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.exec_nurse IS '执行护士';


--
-- Name: COLUMN biz_order_exec.exec_dept; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.exec_dept IS '执行科室';


--
-- Name: COLUMN biz_order_exec.exec_status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.exec_status IS '执行状态';


--
-- Name: COLUMN biz_order_exec.exec_note; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.exec_note IS '执行备注';


--
-- Name: COLUMN biz_order_exec.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_order_exec.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.created_at IS '创建时间';


--
-- Name: COLUMN biz_order_exec.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.created_by IS '创建人';


--
-- Name: COLUMN biz_order_exec.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.updated_at IS '更新时间';


--
-- Name: COLUMN biz_order_exec.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.updated_by IS '更新人';


--
-- Name: COLUMN biz_order_exec.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_order_exec.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_order_exec_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_order_exec_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_order_exec_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_order_exec_id_seq OWNED BY hospital_demo.biz_order_exec.id;


--
-- Name: biz_patient_allergy; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_patient_allergy (
    id bigint NOT NULL,
    patient_id character varying(32) NOT NULL,
    allergy_type character varying(16),
    allergen character varying(200),
    reaction character varying(200),
    severity character varying(8),
    onset_date date,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_patient_allergy; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_patient_allergy IS '患者过敏信息表—— 患者过敏信息';


--
-- Name: COLUMN biz_patient_allergy.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_patient_allergy.allergy_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.allergy_type IS '过敏类型：药物/食物/环境';


--
-- Name: COLUMN biz_patient_allergy.allergen; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.allergen IS '过敏原';


--
-- Name: COLUMN biz_patient_allergy.reaction; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.reaction IS '过敏反应表现';


--
-- Name: COLUMN biz_patient_allergy.severity; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.severity IS '严重程度：轻/中/重';


--
-- Name: COLUMN biz_patient_allergy.onset_date; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.onset_date IS '发现日期';


--
-- Name: COLUMN biz_patient_allergy.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_patient_allergy.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.created_at IS '创建时间';


--
-- Name: COLUMN biz_patient_allergy.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.created_by IS '创建人';


--
-- Name: COLUMN biz_patient_allergy.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.updated_at IS '更新时间';


--
-- Name: COLUMN biz_patient_allergy.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.updated_by IS '更新人';


--
-- Name: COLUMN biz_patient_allergy.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_allergy.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_patient_allergy_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_patient_allergy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_patient_allergy_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_patient_allergy_id_seq OWNED BY hospital_demo.biz_patient_allergy.id;


--
-- Name: biz_patient_master; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_patient_master (
    id bigint NOT NULL,
    patient_id character varying(32) NOT NULL,
    register_no character varying(32),
    patient_name character varying(64) NOT NULL,
    gender_code character varying(8),
    birth_date date,
    id_card_type character varying(8),
    id_card character varying(32),
    marital_status character varying(8),
    nationality character varying(32),
    nation character varying(16),
    occupation character varying(64),
    merge_flag smallint DEFAULT 0 NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_patient_master; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_patient_master IS '患者主索引 EMPI 表—— 患者主索引 EMPI（BIZ_Patient_Master / FHIR Patient）';


--
-- Name: COLUMN biz_patient_master.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.patient_id IS '患者主索引 EMPI（唯一）';


--
-- Name: COLUMN biz_patient_master.register_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.register_no IS '登记号';


--
-- Name: COLUMN biz_patient_master.patient_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.patient_name IS '姓名';


--
-- Name: COLUMN biz_patient_master.gender_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.gender_code IS '性别（码表 CT_Sex）';


--
-- Name: COLUMN biz_patient_master.birth_date; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.birth_date IS '出生日期';


--
-- Name: COLUMN biz_patient_master.id_card_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.id_card_type IS '证件类型（码表）';


--
-- Name: COLUMN biz_patient_master.id_card; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.id_card IS '证件号（脱敏列）';


--
-- Name: COLUMN biz_patient_master.marital_status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.marital_status IS '婚姻状况（码表 CT_Marital）';


--
-- Name: COLUMN biz_patient_master.nationality; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.nationality IS '国籍';


--
-- Name: COLUMN biz_patient_master.nation; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.nation IS '民族（码表 CT_Nation）';


--
-- Name: COLUMN biz_patient_master.occupation; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.occupation IS '职业';


--
-- Name: COLUMN biz_patient_master.merge_flag; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.merge_flag IS 'EMPI 状态：0-正常 1-已合并 2-已拆分';


--
-- Name: COLUMN biz_patient_master.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_patient_master.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.created_at IS '创建时间';


--
-- Name: COLUMN biz_patient_master.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.created_by IS '创建人';


--
-- Name: COLUMN biz_patient_master.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.updated_at IS '更新时间';


--
-- Name: COLUMN biz_patient_master.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.updated_by IS '更新人';


--
-- Name: COLUMN biz_patient_master.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_patient_master.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_patient_master_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_patient_master_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_patient_master_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_patient_master_id_seq OWNED BY hospital_demo.biz_patient_master.id;


--
-- Name: biz_prescription; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_prescription (
    id bigint NOT NULL,
    prescription_no character varying(32) NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    prescription_type character varying(8) NOT NULL,
    doctor_code character varying(32),
    dept_code character varying(16),
    total_amount numeric(10,2),
    prescribe_time timestamp without time zone,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_prescription; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_prescription IS '处方单表—— 处方单（中西药，FHIR MedicationRequest）';


--
-- Name: COLUMN biz_prescription.prescription_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.prescription_no IS '处方号（唯一）';


--
-- Name: COLUMN biz_prescription.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_prescription.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_prescription.prescription_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.prescription_type IS '处方类型：中药/西药';


--
-- Name: COLUMN biz_prescription.doctor_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.doctor_code IS '处方医生';


--
-- Name: COLUMN biz_prescription.dept_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.dept_code IS '开方科室';


--
-- Name: COLUMN biz_prescription.total_amount; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.total_amount IS '处方总金额';


--
-- Name: COLUMN biz_prescription.prescribe_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.prescribe_time IS '开方时间';


--
-- Name: COLUMN biz_prescription.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_prescription.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.created_at IS '创建时间';


--
-- Name: COLUMN biz_prescription.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.created_by IS '创建人';


--
-- Name: COLUMN biz_prescription.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.updated_at IS '更新时间';


--
-- Name: COLUMN biz_prescription.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.updated_by IS '更新人';


--
-- Name: COLUMN biz_prescription.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_prescription_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_prescription_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_prescription_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_prescription_id_seq OWNED BY hospital_demo.biz_prescription.id;


--
-- Name: biz_prescription_item; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_prescription_item (
    id bigint NOT NULL,
    prescription_no character varying(32) NOT NULL,
    item_seq smallint DEFAULT 1 NOT NULL,
    drug_code character varying(32) NOT NULL,
    drug_name character varying(200) NOT NULL,
    spec character varying(100),
    dosage character varying(100),
    quantity numeric(10,2),
    unit character varying(16),
    usage_note character varying(200),
    days smallint,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_prescription_item; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_prescription_item IS '处方明细表—— 处方明细';


--
-- Name: COLUMN biz_prescription_item.prescription_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.prescription_no IS '处方号';


--
-- Name: COLUMN biz_prescription_item.item_seq; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.item_seq IS '明细序号';


--
-- Name: COLUMN biz_prescription_item.drug_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.drug_code IS '药品编码（DIM_Pharmacy 字典）';


--
-- Name: COLUMN biz_prescription_item.drug_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.drug_name IS '药品名称';


--
-- Name: COLUMN biz_prescription_item.spec; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.spec IS '规格';


--
-- Name: COLUMN biz_prescription_item.dosage; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.dosage IS '单次用量';


--
-- Name: COLUMN biz_prescription_item.quantity; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.quantity IS '数量';


--
-- Name: COLUMN biz_prescription_item.unit; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.unit IS '单位';


--
-- Name: COLUMN biz_prescription_item.usage_note; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.usage_note IS '用法说明';


--
-- Name: COLUMN biz_prescription_item.days; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.days IS '用药天数';


--
-- Name: COLUMN biz_prescription_item.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_prescription_item.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.created_at IS '创建时间';


--
-- Name: COLUMN biz_prescription_item.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.created_by IS '创建人';


--
-- Name: COLUMN biz_prescription_item.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.updated_at IS '更新时间';


--
-- Name: COLUMN biz_prescription_item.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.updated_by IS '更新人';


--
-- Name: COLUMN biz_prescription_item.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_prescription_item.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_prescription_item_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_prescription_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_prescription_item_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_prescription_item_id_seq OWNED BY hospital_demo.biz_prescription_item.id;


--
-- Name: biz_surgery_anesthesia; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_surgery_anesthesia (
    id bigint NOT NULL,
    surgery_no character varying(32) NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    surgery_icd character varying(20) NOT NULL,
    surgery_name character varying(200) NOT NULL,
    incision_level character varying(8),
    anesthesia_type character varying(32),
    surgeon_code character varying(32),
    surgeon_name character varying(64),
    surgery_start timestamp without time zone,
    surgery_end timestamp without time zone,
    surgery_status character varying(8) DEFAULT 'PLANNED'::character varying NOT NULL,
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_surgery_anesthesia; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_surgery_anesthesia IS '手术麻醉表—— 手术与麻醉（FHIR Procedure）';


--
-- Name: COLUMN biz_surgery_anesthesia.surgery_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.surgery_no IS '手术号（唯一）';


--
-- Name: COLUMN biz_surgery_anesthesia.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_surgery_anesthesia.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_surgery_anesthesia.surgery_icd; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.surgery_icd IS '手术编码（ICD-9-CM-3）';


--
-- Name: COLUMN biz_surgery_anesthesia.surgery_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.surgery_name IS '手术名称';


--
-- Name: COLUMN biz_surgery_anesthesia.incision_level; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.incision_level IS '切口愈合等级';


--
-- Name: COLUMN biz_surgery_anesthesia.anesthesia_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.anesthesia_type IS '麻醉方式';


--
-- Name: COLUMN biz_surgery_anesthesia.surgeon_code; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.surgeon_code IS '主刀医生编码';


--
-- Name: COLUMN biz_surgery_anesthesia.surgeon_name; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.surgeon_name IS '主刀医生姓名';


--
-- Name: COLUMN biz_surgery_anesthesia.surgery_start; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.surgery_start IS '手术开始时间';


--
-- Name: COLUMN biz_surgery_anesthesia.surgery_end; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.surgery_end IS '手术结束时间';


--
-- Name: COLUMN biz_surgery_anesthesia.surgery_status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.surgery_status IS '状态：PLANNED/DONE/CANCELLED';


--
-- Name: COLUMN biz_surgery_anesthesia.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_surgery_anesthesia.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.created_at IS '创建时间';


--
-- Name: COLUMN biz_surgery_anesthesia.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.created_by IS '创建人';


--
-- Name: COLUMN biz_surgery_anesthesia.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.updated_at IS '更新时间';


--
-- Name: COLUMN biz_surgery_anesthesia.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.updated_by IS '更新人';


--
-- Name: COLUMN biz_surgery_anesthesia.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_surgery_anesthesia.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_surgery_anesthesia_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_surgery_anesthesia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_surgery_anesthesia_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_surgery_anesthesia_id_seq OWNED BY hospital_demo.biz_surgery_anesthesia.id;


--
-- Name: biz_transfusion; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_transfusion (
    id bigint NOT NULL,
    transfusion_no character varying(32) NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    blood_type character varying(8),
    blood_product character varying(32),
    cross_match_time timestamp without time zone,
    transfuse_time timestamp without time zone,
    volume_ml integer,
    reaction character varying(200),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_transfusion; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_transfusion IS '输血记录表—— 输血记录';


--
-- Name: COLUMN biz_transfusion.transfusion_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.transfusion_no IS '输血单号（唯一）';


--
-- Name: COLUMN biz_transfusion.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_transfusion.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_transfusion.blood_type; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.blood_type IS '血型（ABO/Rh）';


--
-- Name: COLUMN biz_transfusion.blood_product; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.blood_product IS '血液成分：红细胞/血浆/血小板等';


--
-- Name: COLUMN biz_transfusion.cross_match_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.cross_match_time IS '交叉配血时间';


--
-- Name: COLUMN biz_transfusion.transfuse_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.transfuse_time IS '输血执行时间';


--
-- Name: COLUMN biz_transfusion.volume_ml; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.volume_ml IS '输注量（ml）';


--
-- Name: COLUMN biz_transfusion.reaction; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.reaction IS '输血不良反应记录';


--
-- Name: COLUMN biz_transfusion.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_transfusion.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.created_at IS '创建时间';


--
-- Name: COLUMN biz_transfusion.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.created_by IS '创建人';


--
-- Name: COLUMN biz_transfusion.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.updated_at IS '更新时间';


--
-- Name: COLUMN biz_transfusion.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.updated_by IS '更新人';


--
-- Name: COLUMN biz_transfusion.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_transfusion.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_transfusion_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_transfusion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_transfusion_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_transfusion_id_seq OWNED BY hospital_demo.biz_transfusion.id;


--
-- Name: biz_vital_signs; Type: TABLE; Schema: hospital_demo; Owner: -
--

CREATE TABLE hospital_demo.biz_vital_signs (
    id bigint NOT NULL,
    encounter_no character varying(32) NOT NULL,
    patient_id character varying(32) NOT NULL,
    measure_time timestamp without time zone NOT NULL,
    temperature numeric(4,1),
    pulse smallint,
    respiration smallint,
    sbp smallint,
    dbp smallint,
    spo2 smallint,
    pain_score smallint,
    intake_ml numeric(10,2),
    output_ml numeric(10,2),
    status smallint DEFAULT 1 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(100) DEFAULT '系统'::character varying NOT NULL,
    deleted_at timestamp without time zone
);


--
-- Name: TABLE biz_vital_signs; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON TABLE hospital_demo.biz_vital_signs IS '生命体征表—— 生命体征（体温单，FHIR Observation-VitalSigns）';


--
-- Name: COLUMN biz_vital_signs.encounter_no; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.encounter_no IS '就诊号';


--
-- Name: COLUMN biz_vital_signs.patient_id; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.patient_id IS '患者 EMPI';


--
-- Name: COLUMN biz_vital_signs.measure_time; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.measure_time IS '测量时间';


--
-- Name: COLUMN biz_vital_signs.temperature; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.temperature IS '体温（℃）';


--
-- Name: COLUMN biz_vital_signs.pulse; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.pulse IS '脉搏（次/分）';


--
-- Name: COLUMN biz_vital_signs.respiration; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.respiration IS '呼吸（次/分）';


--
-- Name: COLUMN biz_vital_signs.sbp; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.sbp IS '收缩压（mmHg）';


--
-- Name: COLUMN biz_vital_signs.dbp; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.dbp IS '舒张压（mmHg）';


--
-- Name: COLUMN biz_vital_signs.spo2; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.spo2 IS '血氧饱和度（%）';


--
-- Name: COLUMN biz_vital_signs.pain_score; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.pain_score IS '疼痛评分';


--
-- Name: COLUMN biz_vital_signs.intake_ml; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.intake_ml IS '入量（ml）';


--
-- Name: COLUMN biz_vital_signs.output_ml; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.output_ml IS '出量（ml）';


--
-- Name: COLUMN biz_vital_signs.status; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.status IS '状态：1-正常 0-停用';


--
-- Name: COLUMN biz_vital_signs.created_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.created_at IS '创建时间';


--
-- Name: COLUMN biz_vital_signs.created_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.created_by IS '创建人';


--
-- Name: COLUMN biz_vital_signs.updated_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.updated_at IS '更新时间';


--
-- Name: COLUMN biz_vital_signs.updated_by; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.updated_by IS '更新人';


--
-- Name: COLUMN biz_vital_signs.deleted_at; Type: COMMENT; Schema: hospital_demo; Owner: -
--

COMMENT ON COLUMN hospital_demo.biz_vital_signs.deleted_at IS '软删除时间（非空=已删除）';


--
-- Name: biz_vital_signs_id_seq; Type: SEQUENCE; Schema: hospital_demo; Owner: -
--

CREATE SEQUENCE hospital_demo.biz_vital_signs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_vital_signs_id_seq; Type: SEQUENCE OWNED BY; Schema: hospital_demo; Owner: -
--

ALTER SEQUENCE hospital_demo.biz_vital_signs_id_seq OWNED BY hospital_demo.biz_vital_signs.id;


--
-- Name: api_call_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_call_log (
    id bigint NOT NULL,
    api_id bigint,
    api_code character varying(64),
    app_id character varying(64),
    params text,
    duration_ms integer,
    row_count integer,
    success boolean DEFAULT true,
    masked_columns character varying(255),
    error_msg text,
    client_ip character varying(64),
    called_at timestamp without time zone DEFAULT now()
);


--
-- Name: api_call_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.api_call_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_call_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.api_call_log_id_seq OWNED BY public.api_call_log.id;


--
-- Name: api_client; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_client (
    id bigint NOT NULL,
    app_id character varying(64) NOT NULL,
    app_key character varying(128) NOT NULL,
    app_name character varying(128),
    contact character varying(64),
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    last_call_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: api_client_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.api_client_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_client_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.api_client_id_seq OWNED BY public.api_client.id;


--
-- Name: api_service; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_service (
    id bigint NOT NULL,
    api_code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    asset_id bigint,
    sql_text text NOT NULL,
    params_json text,
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    rate_limit integer DEFAULT 60,
    owner character varying(64),
    description text,
    call_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: api_service_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.api_service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: api_service_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.api_service_id_seq OWNED BY public.api_service.id;


--
-- Name: asset; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asset (
    id bigint NOT NULL,
    asset_code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    domain_id bigint,
    asset_type character varying(32) NOT NULL,
    description text,
    owner character varying(64),
    department character varying(64),
    security_level character varying(8),
    status character varying(16) DEFAULT 'PUBLISHED'::character varying,
    source_table_id bigint,
    tags character varying(255),
    view_count integer DEFAULT 0,
    favorite boolean DEFAULT false,
    quality_score numeric(5,2),
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: asset_domain; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asset_domain (
    id bigint NOT NULL,
    parent_id bigint,
    code character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    description text,
    sort_no integer DEFAULT 0
);


--
-- Name: asset_domain_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.asset_domain_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_domain_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.asset_domain_id_seq OWNED BY public.asset_domain.id;


--
-- Name: asset_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.asset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asset_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.asset_id_seq OWNED BY public.asset.id;


--
-- Name: biz_patient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.biz_patient (
    id bigint NOT NULL,
    patient_name character varying(64),
    id_card character varying(32),
    phone character varying(20),
    gender character(1),
    birth_date date,
    address character varying(255),
    dept_code character varying(16),
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: biz_patient_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.biz_patient_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: biz_patient_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.biz_patient_id_seq OWNED BY public.biz_patient.id;


--
-- Name: data_source; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_source (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    type character varying(32) NOT NULL,
    host character varying(128),
    port integer,
    db_name character varying(128),
    schema_name character varying(64),
    username character varying(64),
    password character varying(255),
    status character varying(32) DEFAULT 'CONNECTED'::character varying,
    last_sync_at timestamp without time zone,
    table_count integer DEFAULT 0,
    description text,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: data_source_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.data_source_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: data_source_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.data_source_id_seq OWNED BY public.data_source.id;


--
-- Name: data_standard; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_standard (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    category_id bigint,
    std_type character varying(32),
    rule_content text,
    example_value character varying(255),
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    version character varying(16) DEFAULT 'v1.0'::character varying,
    owner character varying(64),
    effective_date date,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: data_standard_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.data_standard_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: data_standard_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.data_standard_id_seq OWNED BY public.data_standard.id;


--
-- Name: lineage_edge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lineage_edge (
    id bigint NOT NULL,
    source_asset_id bigint NOT NULL,
    source_column character varying(128),
    target_asset_id bigint NOT NULL,
    target_column character varying(128),
    level character varying(16) DEFAULT 'TABLE'::character varying,
    job_name character varying(128),
    transform_type character varying(32),
    sql_text text,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: lineage_edge_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lineage_edge_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lineage_edge_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lineage_edge_id_seq OWNED BY public.lineage_edge.id;


--
-- Name: meta_column; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meta_column (
    id bigint NOT NULL,
    table_id bigint NOT NULL,
    ordinal integer,
    column_name character varying(128) NOT NULL,
    data_type character varying(64),
    length integer,
    nullable boolean DEFAULT true,
    primary_key boolean DEFAULT false,
    column_comment character varying(512)
);


--
-- Name: meta_column_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.meta_column_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: meta_column_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.meta_column_id_seq OWNED BY public.meta_column.id;


--
-- Name: meta_table; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.meta_table (
    id bigint NOT NULL,
    source_id bigint NOT NULL,
    schema_name character varying(64),
    table_name character varying(128) NOT NULL,
    table_comment character varying(512),
    row_count bigint DEFAULT 0,
    column_count integer DEFAULT 0,
    owner character varying(64),
    collected_at timestamp without time zone
);


--
-- Name: meta_table_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.meta_table_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: meta_table_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.meta_table_id_seq OWNED BY public.meta_table.id;


--
-- Name: quality_result; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quality_result (
    id bigint NOT NULL,
    rule_id bigint NOT NULL,
    run_at timestamp without time zone DEFAULT now(),
    total_count bigint DEFAULT 0,
    pass_count bigint DEFAULT 0,
    fail_count bigint DEFAULT 0,
    pass_rate numeric(5,2),
    status character varying(16),
    detail text
);


--
-- Name: quality_result_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.quality_result_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: quality_result_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.quality_result_id_seq OWNED BY public.quality_result.id;


--
-- Name: quality_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quality_rule (
    id bigint NOT NULL,
    name character varying(200) NOT NULL,
    asset_id bigint,
    column_name character varying(128),
    rule_type character varying(32) NOT NULL,
    rule_expr character varying(512),
    threshold numeric(5,2) DEFAULT 100,
    severity character varying(16) DEFAULT 'MEDIUM'::character varying,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: quality_rule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.quality_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: quality_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.quality_rule_id_seq OWNED BY public.quality_rule.id;


--
-- Name: sched_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sched_job (
    id bigint NOT NULL,
    job_code character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    job_type character varying(64) NOT NULL,
    cron_expr character varying(64) NOT NULL,
    enabled boolean DEFAULT true,
    description text,
    last_run_at timestamp without time zone,
    last_status character varying(16),
    last_message text,
    run_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: sched_job_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sched_job_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sched_job_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sched_job_id_seq OWNED BY public.sched_job.id;


--
-- Name: sched_job_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sched_job_log (
    id bigint NOT NULL,
    job_id bigint NOT NULL,
    started_at timestamp without time zone DEFAULT now(),
    finished_at timestamp without time zone,
    duration_ms bigint,
    status character varying(16),
    message text
);


--
-- Name: sched_job_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sched_job_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sched_job_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sched_job_log_id_seq OWNED BY public.sched_job_log.id;


--
-- Name: security_level; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.security_level (
    id bigint NOT NULL,
    code character varying(8) NOT NULL,
    name character varying(32) NOT NULL,
    description text,
    color character varying(16),
    sort_no integer DEFAULT 0
);


--
-- Name: security_level_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.security_level_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_level_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.security_level_id_seq OWNED BY public.security_level.id;


--
-- Name: security_policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.security_policy (
    id bigint NOT NULL,
    asset_id bigint NOT NULL,
    column_name character varying(128),
    level_code character varying(8),
    desensitize character varying(32) DEFAULT 'NONE'::character varying,
    authorized_roles character varying(255),
    audit_enabled boolean DEFAULT true,
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: security_policy_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.security_policy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_policy_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.security_policy_id_seq OWNED BY public.security_policy.id;


--
-- Name: standard_category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.standard_category (
    id bigint NOT NULL,
    parent_id bigint,
    code character varying(64),
    name character varying(128) NOT NULL,
    description text
);


--
-- Name: standard_category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.standard_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: standard_category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.standard_category_id_seq OWNED BY public.standard_category.id;


--
-- Name: standard_mapping; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.standard_mapping (
    id bigint NOT NULL,
    standard_id bigint NOT NULL,
    asset_id bigint NOT NULL,
    column_name character varying(128),
    conform_status character varying(16) DEFAULT 'CONFORM'::character varying,
    checked_at timestamp without time zone DEFAULT now()
);


--
-- Name: standard_mapping_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.standard_mapping_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: standard_mapping_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.standard_mapping_id_seq OWNED BY public.standard_mapping.id;


--
-- Name: sys_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user (
    id bigint NOT NULL,
    username character varying(64) NOT NULL,
    password_hash character varying(64) NOT NULL,
    salt character varying(32) NOT NULL,
    display_name character varying(64),
    role character varying(32) DEFAULT 'ANALYST'::character varying NOT NULL,
    department character varying(64),
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    last_login_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: sys_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_user_id_seq OWNED BY public.sys_user.id;


--
-- Name: asset_category id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_category ALTER COLUMN id SET DEFAULT nextval('dam_dam.asset_category_id_seq'::regclass);


--
-- Name: asset_category_rel id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_category_rel ALTER COLUMN id SET DEFAULT nextval('dam_dam.asset_category_rel_id_seq'::regclass);


--
-- Name: asset_favorite id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_favorite ALTER COLUMN id SET DEFAULT nextval('dam_dam.asset_favorite_id_seq'::regclass);


--
-- Name: asset_info id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_info ALTER COLUMN id SET DEFAULT nextval('dam_dam.asset_info_id_seq'::regclass);


--
-- Name: asset_rating id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_rating ALTER COLUMN id SET DEFAULT nextval('dam_dam.asset_rating_id_seq'::regclass);


--
-- Name: asset_statistics id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_statistics ALTER COLUMN id SET DEFAULT nextval('dam_dam.asset_statistics_id_seq'::regclass);


--
-- Name: asset_tag id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_tag ALTER COLUMN id SET DEFAULT nextval('dam_dam.asset_tag_id_seq'::regclass);


--
-- Name: asset_tag_rel id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_tag_rel ALTER COLUMN id SET DEFAULT nextval('dam_dam.asset_tag_rel_id_seq'::regclass);


--
-- Name: asset_usage_log id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_usage_log ALTER COLUMN id SET DEFAULT nextval('dam_dam.asset_usage_log_id_seq'::regclass);


--
-- Name: collect_task id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.collect_task ALTER COLUMN id SET DEFAULT nextval('dam_dam.collect_task_id_seq'::regclass);


--
-- Name: collect_task_log id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.collect_task_log ALTER COLUMN id SET DEFAULT nextval('dam_dam.collect_task_log_id_seq'::regclass);


--
-- Name: metadata_business id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_business ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_business_id_seq'::regclass);


--
-- Name: metadata_business_table_rel id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_business_table_rel ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_business_table_rel_id_seq'::regclass);


--
-- Name: metadata_category id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_category ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_category_id_seq'::regclass);


--
-- Name: metadata_collection id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_collection ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_collection_id_seq'::regclass);


--
-- Name: metadata_column id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_column ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_column_id_seq'::regclass);


--
-- Name: metadata_datasource id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_datasource ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_datasource_id_seq'::regclass);


--
-- Name: metadata_destruction_record id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_destruction_record ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_destruction_record_id_seq'::regclass);


--
-- Name: metadata_lifecycle_policy id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_lifecycle_policy ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_lifecycle_policy_id_seq'::regclass);


--
-- Name: metadata_lineage id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_lineage ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_lineage_id_seq'::regclass);


--
-- Name: metadata_management id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_management ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_management_id_seq'::regclass);


--
-- Name: metadata_quality_check_result id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_quality_check_result ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_quality_check_result_id_seq'::regclass);


--
-- Name: metadata_quality_rule id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_quality_rule ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_quality_rule_id_seq'::regclass);


--
-- Name: metadata_schema id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_schema ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_schema_id_seq'::regclass);


--
-- Name: metadata_table id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_table ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_table_id_seq'::regclass);


--
-- Name: metadata_version id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_version ALTER COLUMN id SET DEFAULT nextval('dam_dam.metadata_version_id_seq'::regclass);


--
-- Name: source_system id; Type: DEFAULT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.source_system ALTER COLUMN id SET DEFAULT nextval('dam_dam.source_system_id_seq'::regclass);


--
-- Name: report_catalog id; Type: DEFAULT; Schema: dam_dan; Owner: -
--

ALTER TABLE ONLY dam_dan.report_catalog ALTER COLUMN id SET DEFAULT nextval('dam_dan.report_catalog_id_seq'::regclass);


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
-- Name: report_dimension id; Type: DEFAULT; Schema: dam_dimension; Owner: -
--

ALTER TABLE ONLY dam_dimension.report_dimension ALTER COLUMN id SET DEFAULT nextval('dam_dimension.report_dimension_id_seq'::regclass);


--
-- Name: report_dimension_item id; Type: DEFAULT; Schema: dam_dimension; Owner: -
--

ALTER TABLE ONLY dam_dimension.report_dimension_item ALTER COLUMN id SET DEFAULT nextval('dam_dimension.report_dimension_item_id_seq'::regclass);


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
-- Name: model_column_mapping id; Type: DEFAULT; Schema: dam_dpp; Owner: -
--

ALTER TABLE ONLY dam_dpp.model_column_mapping ALTER COLUMN id SET DEFAULT nextval('dam_dpp.model_column_mapping_id_seq'::regclass);


--
-- Name: model_dimension id; Type: DEFAULT; Schema: dam_dpp; Owner: -
--

ALTER TABLE ONLY dam_dpp.model_dimension ALTER COLUMN id SET DEFAULT nextval('dam_dpp.model_dimension_id_seq'::regclass);


--
-- Name: model_domain id; Type: DEFAULT; Schema: dam_dpp; Owner: -
--

ALTER TABLE ONLY dam_dpp.model_domain ALTER COLUMN id SET DEFAULT nextval('dam_dpp.model_domain_id_seq'::regclass);


--
-- Name: model_fact id; Type: DEFAULT; Schema: dam_dpp; Owner: -
--

ALTER TABLE ONLY dam_dpp.model_fact ALTER COLUMN id SET DEFAULT nextval('dam_dpp.model_fact_id_seq'::regclass);


--
-- Name: api_call_log id; Type: DEFAULT; Schema: dam_service; Owner: -
--

ALTER TABLE ONLY dam_service.api_call_log ALTER COLUMN id SET DEFAULT nextval('dam_service.api_call_log_id_seq'::regclass);


--
-- Name: api_client id; Type: DEFAULT; Schema: dam_service; Owner: -
--

ALTER TABLE ONLY dam_service.api_client ALTER COLUMN id SET DEFAULT nextval('dam_service.api_client_id_seq'::regclass);


--
-- Name: api_service id; Type: DEFAULT; Schema: dam_service; Owner: -
--

ALTER TABLE ONLY dam_service.api_service ALTER COLUMN id SET DEFAULT nextval('dam_service.api_service_id_seq'::regclass);


--
-- Name: dim_code id; Type: DEFAULT; Schema: dim; Owner: -
--

ALTER TABLE ONLY dim.dim_code ALTER COLUMN id SET DEFAULT nextval('dim.dim_code_id_seq'::regclass);


--
-- Name: biz_aux_exam id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_aux_exam ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_aux_exam_id_seq'::regclass);


--
-- Name: biz_clinical_order id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_clinical_order ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_clinical_order_id_seq'::regclass);


--
-- Name: biz_diagnosis id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_diagnosis ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_diagnosis_id_seq'::regclass);


--
-- Name: biz_emr_document id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_emr_document ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_emr_document_id_seq'::regclass);


--
-- Name: biz_encounter id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_encounter ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_encounter_id_seq'::regclass);


--
-- Name: biz_encounter_transfer id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_encounter_transfer ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_encounter_transfer_id_seq'::regclass);


--
-- Name: biz_imaging_exam id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_imaging_exam ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_imaging_exam_id_seq'::regclass);


--
-- Name: biz_lab_report id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_lab_report ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_lab_report_id_seq'::regclass);


--
-- Name: biz_lab_result id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_lab_result ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_lab_result_id_seq'::regclass);


--
-- Name: biz_order_exec id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_order_exec ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_order_exec_id_seq'::regclass);


--
-- Name: biz_patient_allergy id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_patient_allergy ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_patient_allergy_id_seq'::regclass);


--
-- Name: biz_patient_master id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_patient_master ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_patient_master_id_seq'::regclass);


--
-- Name: biz_prescription id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_prescription ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_prescription_id_seq'::regclass);


--
-- Name: biz_prescription_item id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_prescription_item ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_prescription_item_id_seq'::regclass);


--
-- Name: biz_surgery_anesthesia id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_surgery_anesthesia ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_surgery_anesthesia_id_seq'::regclass);


--
-- Name: biz_transfusion id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_transfusion ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_transfusion_id_seq'::regclass);


--
-- Name: biz_vital_signs id; Type: DEFAULT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_vital_signs ALTER COLUMN id SET DEFAULT nextval('hospital_demo.biz_vital_signs_id_seq'::regclass);


--
-- Name: api_call_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_call_log ALTER COLUMN id SET DEFAULT nextval('public.api_call_log_id_seq'::regclass);


--
-- Name: api_client id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_client ALTER COLUMN id SET DEFAULT nextval('public.api_client_id_seq'::regclass);


--
-- Name: api_service id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_service ALTER COLUMN id SET DEFAULT nextval('public.api_service_id_seq'::regclass);


--
-- Name: asset id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asset ALTER COLUMN id SET DEFAULT nextval('public.asset_id_seq'::regclass);


--
-- Name: asset_domain id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asset_domain ALTER COLUMN id SET DEFAULT nextval('public.asset_domain_id_seq'::regclass);


--
-- Name: biz_patient id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.biz_patient ALTER COLUMN id SET DEFAULT nextval('public.biz_patient_id_seq'::regclass);


--
-- Name: data_source id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_source ALTER COLUMN id SET DEFAULT nextval('public.data_source_id_seq'::regclass);


--
-- Name: data_standard id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_standard ALTER COLUMN id SET DEFAULT nextval('public.data_standard_id_seq'::regclass);


--
-- Name: lineage_edge id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lineage_edge ALTER COLUMN id SET DEFAULT nextval('public.lineage_edge_id_seq'::regclass);


--
-- Name: meta_column id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meta_column ALTER COLUMN id SET DEFAULT nextval('public.meta_column_id_seq'::regclass);


--
-- Name: meta_table id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meta_table ALTER COLUMN id SET DEFAULT nextval('public.meta_table_id_seq'::regclass);


--
-- Name: quality_result id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_result ALTER COLUMN id SET DEFAULT nextval('public.quality_result_id_seq'::regclass);


--
-- Name: quality_rule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_rule ALTER COLUMN id SET DEFAULT nextval('public.quality_rule_id_seq'::regclass);


--
-- Name: sched_job id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sched_job ALTER COLUMN id SET DEFAULT nextval('public.sched_job_id_seq'::regclass);


--
-- Name: sched_job_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sched_job_log ALTER COLUMN id SET DEFAULT nextval('public.sched_job_log_id_seq'::regclass);


--
-- Name: security_level id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_level ALTER COLUMN id SET DEFAULT nextval('public.security_level_id_seq'::regclass);


--
-- Name: security_policy id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_policy ALTER COLUMN id SET DEFAULT nextval('public.security_policy_id_seq'::regclass);


--
-- Name: standard_category id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.standard_category ALTER COLUMN id SET DEFAULT nextval('public.standard_category_id_seq'::regclass);


--
-- Name: standard_mapping id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.standard_mapping ALTER COLUMN id SET DEFAULT nextval('public.standard_mapping_id_seq'::regclass);


--
-- Name: sys_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user ALTER COLUMN id SET DEFAULT nextval('public.sys_user_id_seq'::regclass);


--
-- Name: OE_OrdExec OE_OrdExec_pkey; Type: CONSTRAINT; Schema: PROD.CIS_Model; Owner: -
--

ALTER TABLE ONLY "PROD.CIS_Model"."OE_OrdExec"
    ADD CONSTRAINT "OE_OrdExec_pkey" PRIMARY KEY (execid);


--
-- Name: OE_Order OE_Order_pkey; Type: CONSTRAINT; Schema: PROD.CIS_Model; Owner: -
--

ALTER TABLE ONLY "PROD.CIS_Model"."OE_Order"
    ADD CONSTRAINT "OE_Order_pkey" PRIMARY KEY (ordid);


--
-- Name: PA_Adm PA_Adm_pkey; Type: CONSTRAINT; Schema: PROD.CIS_Model; Owner: -
--

ALTER TABLE ONLY "PROD.CIS_Model"."PA_Adm"
    ADD CONSTRAINT "PA_Adm_pkey" PRIMARY KEY (admid);


--
-- Name: PA_PatMas PA_PatMas_pkey; Type: CONSTRAINT; Schema: PROD.CIS_Model; Owner: -
--

ALTER TABLE ONLY "PROD.CIS_Model"."PA_PatMas"
    ADD CONSTRAINT "PA_PatMas_pkey" PRIMARY KEY (patid);


--
-- Name: asset_category asset_category_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_category
    ADD CONSTRAINT asset_category_pkey PRIMARY KEY (id);


--
-- Name: asset_category_rel asset_category_rel_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_category_rel
    ADD CONSTRAINT asset_category_rel_pkey PRIMARY KEY (id);


--
-- Name: asset_favorite asset_favorite_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_favorite
    ADD CONSTRAINT asset_favorite_pkey PRIMARY KEY (id);


--
-- Name: asset_info asset_info_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_info
    ADD CONSTRAINT asset_info_pkey PRIMARY KEY (id);


--
-- Name: asset_rating asset_rating_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_rating
    ADD CONSTRAINT asset_rating_pkey PRIMARY KEY (id);


--
-- Name: asset_statistics asset_statistics_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_statistics
    ADD CONSTRAINT asset_statistics_pkey PRIMARY KEY (id);


--
-- Name: asset_tag asset_tag_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_tag
    ADD CONSTRAINT asset_tag_pkey PRIMARY KEY (id);


--
-- Name: asset_tag_rel asset_tag_rel_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_tag_rel
    ADD CONSTRAINT asset_tag_rel_pkey PRIMARY KEY (id);


--
-- Name: asset_usage_log asset_usage_log_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.asset_usage_log
    ADD CONSTRAINT asset_usage_log_pkey PRIMARY KEY (id);


--
-- Name: collect_task_log collect_task_log_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.collect_task_log
    ADD CONSTRAINT collect_task_log_pkey PRIMARY KEY (id);


--
-- Name: collect_task collect_task_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.collect_task
    ADD CONSTRAINT collect_task_pkey PRIMARY KEY (id);


--
-- Name: collect_task collect_task_task_code_key; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.collect_task
    ADD CONSTRAINT collect_task_task_code_key UNIQUE (task_code);


--
-- Name: metadata_business metadata_business_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_business
    ADD CONSTRAINT metadata_business_pkey PRIMARY KEY (id);


--
-- Name: metadata_business_table_rel metadata_business_table_rel_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_business_table_rel
    ADD CONSTRAINT metadata_business_table_rel_pkey PRIMARY KEY (id);


--
-- Name: metadata_category metadata_category_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_category
    ADD CONSTRAINT metadata_category_pkey PRIMARY KEY (id);


--
-- Name: metadata_collection metadata_collection_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_collection
    ADD CONSTRAINT metadata_collection_pkey PRIMARY KEY (id);


--
-- Name: metadata_column metadata_column_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_column
    ADD CONSTRAINT metadata_column_pkey PRIMARY KEY (id);


--
-- Name: metadata_datasource metadata_datasource_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_datasource
    ADD CONSTRAINT metadata_datasource_pkey PRIMARY KEY (id);


--
-- Name: metadata_destruction_record metadata_destruction_record_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_destruction_record
    ADD CONSTRAINT metadata_destruction_record_pkey PRIMARY KEY (id);


--
-- Name: metadata_lifecycle_policy metadata_lifecycle_policy_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_lifecycle_policy
    ADD CONSTRAINT metadata_lifecycle_policy_pkey PRIMARY KEY (id);


--
-- Name: metadata_lineage metadata_lineage_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_lineage
    ADD CONSTRAINT metadata_lineage_pkey PRIMARY KEY (id);


--
-- Name: metadata_management metadata_management_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_management
    ADD CONSTRAINT metadata_management_pkey PRIMARY KEY (id);


--
-- Name: metadata_quality_check_result metadata_quality_check_result_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_quality_check_result
    ADD CONSTRAINT metadata_quality_check_result_pkey PRIMARY KEY (id);


--
-- Name: metadata_quality_rule metadata_quality_rule_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_quality_rule
    ADD CONSTRAINT metadata_quality_rule_pkey PRIMARY KEY (id);


--
-- Name: metadata_schema metadata_schema_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_schema
    ADD CONSTRAINT metadata_schema_pkey PRIMARY KEY (id);


--
-- Name: metadata_table metadata_table_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_table
    ADD CONSTRAINT metadata_table_pkey PRIMARY KEY (id);


--
-- Name: metadata_version metadata_version_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.metadata_version
    ADD CONSTRAINT metadata_version_pkey PRIMARY KEY (id);


--
-- Name: source_system source_system_pkey; Type: CONSTRAINT; Schema: dam_dam; Owner: -
--

ALTER TABLE ONLY dam_dam.source_system
    ADD CONSTRAINT source_system_pkey PRIMARY KEY (id);


--
-- Name: report_catalog report_catalog_pkey; Type: CONSTRAINT; Schema: dam_dan; Owner: -
--

ALTER TABLE ONLY dam_dan.report_catalog
    ADD CONSTRAINT report_catalog_pkey PRIMARY KEY (id);


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
-- Name: report_dimension_item report_dimension_item_pkey; Type: CONSTRAINT; Schema: dam_dimension; Owner: -
--

ALTER TABLE ONLY dam_dimension.report_dimension_item
    ADD CONSTRAINT report_dimension_item_pkey PRIMARY KEY (id);


--
-- Name: report_dimension report_dimension_pkey; Type: CONSTRAINT; Schema: dam_dimension; Owner: -
--

ALTER TABLE ONLY dam_dimension.report_dimension
    ADD CONSTRAINT report_dimension_pkey PRIMARY KEY (id);


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
-- Name: model_column_mapping model_column_mapping_pkey; Type: CONSTRAINT; Schema: dam_dpp; Owner: -
--

ALTER TABLE ONLY dam_dpp.model_column_mapping
    ADD CONSTRAINT model_column_mapping_pkey PRIMARY KEY (id);


--
-- Name: model_dimension model_dimension_pkey; Type: CONSTRAINT; Schema: dam_dpp; Owner: -
--

ALTER TABLE ONLY dam_dpp.model_dimension
    ADD CONSTRAINT model_dimension_pkey PRIMARY KEY (id);


--
-- Name: model_domain model_domain_pkey; Type: CONSTRAINT; Schema: dam_dpp; Owner: -
--

ALTER TABLE ONLY dam_dpp.model_domain
    ADD CONSTRAINT model_domain_pkey PRIMARY KEY (id);


--
-- Name: model_fact model_fact_pkey; Type: CONSTRAINT; Schema: dam_dpp; Owner: -
--

ALTER TABLE ONLY dam_dpp.model_fact
    ADD CONSTRAINT model_fact_pkey PRIMARY KEY (id);


--
-- Name: api_call_log api_call_log_pkey; Type: CONSTRAINT; Schema: dam_service; Owner: -
--

ALTER TABLE ONLY dam_service.api_call_log
    ADD CONSTRAINT api_call_log_pkey PRIMARY KEY (id);


--
-- Name: api_client api_client_pkey; Type: CONSTRAINT; Schema: dam_service; Owner: -
--

ALTER TABLE ONLY dam_service.api_client
    ADD CONSTRAINT api_client_pkey PRIMARY KEY (id);


--
-- Name: api_service api_service_pkey; Type: CONSTRAINT; Schema: dam_service; Owner: -
--

ALTER TABLE ONLY dam_service.api_service
    ADD CONSTRAINT api_service_pkey PRIMARY KEY (id);


--
-- Name: api_client uq_api_client_app_id; Type: CONSTRAINT; Schema: dam_service; Owner: -
--

ALTER TABLE ONLY dam_service.api_client
    ADD CONSTRAINT uq_api_client_app_id UNIQUE (app_id);


--
-- Name: api_service uq_api_service_api_code; Type: CONSTRAINT; Schema: dam_service; Owner: -
--

ALTER TABLE ONLY dam_service.api_service
    ADD CONSTRAINT uq_api_service_api_code UNIQUE (api_code);


--
-- Name: dim_code dim_code_pkey; Type: CONSTRAINT; Schema: dim; Owner: -
--

ALTER TABLE ONLY dim.dim_code
    ADD CONSTRAINT dim_code_pkey PRIMARY KEY (id);


--
-- Name: dim_department dim_department_pkey; Type: CONSTRAINT; Schema: dim; Owner: -
--

ALTER TABLE ONLY dim.dim_department
    ADD CONSTRAINT dim_department_pkey PRIMARY KEY (dept_code);


--
-- Name: dim_diagnosis_icd10 dim_diagnosis_icd10_pkey; Type: CONSTRAINT; Schema: dim; Owner: -
--

ALTER TABLE ONLY dim.dim_diagnosis_icd10
    ADD CONSTRAINT dim_diagnosis_icd10_pkey PRIMARY KEY (icd_code);


--
-- Name: dim_surgery_icd9cm3 dim_surgery_icd9cm3_pkey; Type: CONSTRAINT; Schema: dim; Owner: -
--

ALTER TABLE ONLY dim.dim_surgery_icd9cm3
    ADD CONSTRAINT dim_surgery_icd9cm3_pkey PRIMARY KEY (surg_code);


--
-- Name: dwd_clinical_order dwd_clinical_order_pkey; Type: CONSTRAINT; Schema: dwd; Owner: -
--

ALTER TABLE ONLY dwd.dwd_clinical_order
    ADD CONSTRAINT dwd_clinical_order_pkey PRIMARY KEY (ordid);


--
-- Name: dwd_encounter dwd_encounter_pkey; Type: CONSTRAINT; Schema: dwd; Owner: -
--

ALTER TABLE ONLY dwd.dwd_encounter
    ADD CONSTRAINT dwd_encounter_pkey PRIMARY KEY (admid);


--
-- Name: dwd_patient_master dwd_patient_master_pkey; Type: CONSTRAINT; Schema: dwd; Owner: -
--

ALTER TABLE ONLY dwd.dwd_patient_master
    ADD CONSTRAINT dwd_patient_master_pkey PRIMARY KEY (patid);


--
-- Name: biz_aux_exam biz_aux_exam_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_aux_exam
    ADD CONSTRAINT biz_aux_exam_pkey PRIMARY KEY (id);


--
-- Name: biz_clinical_order biz_clinical_order_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_clinical_order
    ADD CONSTRAINT biz_clinical_order_pkey PRIMARY KEY (id);


--
-- Name: biz_diagnosis biz_diagnosis_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_diagnosis
    ADD CONSTRAINT biz_diagnosis_pkey PRIMARY KEY (id);


--
-- Name: biz_emr_document biz_emr_document_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_emr_document
    ADD CONSTRAINT biz_emr_document_pkey PRIMARY KEY (id);


--
-- Name: biz_encounter biz_encounter_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_encounter
    ADD CONSTRAINT biz_encounter_pkey PRIMARY KEY (id);


--
-- Name: biz_encounter_transfer biz_encounter_transfer_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_encounter_transfer
    ADD CONSTRAINT biz_encounter_transfer_pkey PRIMARY KEY (id);


--
-- Name: biz_imaging_exam biz_imaging_exam_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_imaging_exam
    ADD CONSTRAINT biz_imaging_exam_pkey PRIMARY KEY (id);


--
-- Name: biz_lab_report biz_lab_report_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_lab_report
    ADD CONSTRAINT biz_lab_report_pkey PRIMARY KEY (id);


--
-- Name: biz_lab_result biz_lab_result_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_lab_result
    ADD CONSTRAINT biz_lab_result_pkey PRIMARY KEY (id);


--
-- Name: biz_order_exec biz_order_exec_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_order_exec
    ADD CONSTRAINT biz_order_exec_pkey PRIMARY KEY (id);


--
-- Name: biz_patient_allergy biz_patient_allergy_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_patient_allergy
    ADD CONSTRAINT biz_patient_allergy_pkey PRIMARY KEY (id);


--
-- Name: biz_patient_master biz_patient_master_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_patient_master
    ADD CONSTRAINT biz_patient_master_pkey PRIMARY KEY (id);


--
-- Name: biz_prescription_item biz_prescription_item_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_prescription_item
    ADD CONSTRAINT biz_prescription_item_pkey PRIMARY KEY (id);


--
-- Name: biz_prescription biz_prescription_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_prescription
    ADD CONSTRAINT biz_prescription_pkey PRIMARY KEY (id);


--
-- Name: biz_surgery_anesthesia biz_surgery_anesthesia_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_surgery_anesthesia
    ADD CONSTRAINT biz_surgery_anesthesia_pkey PRIMARY KEY (id);


--
-- Name: biz_transfusion biz_transfusion_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_transfusion
    ADD CONSTRAINT biz_transfusion_pkey PRIMARY KEY (id);


--
-- Name: biz_vital_signs biz_vital_signs_pkey; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_vital_signs
    ADD CONSTRAINT biz_vital_signs_pkey PRIMARY KEY (id);


--
-- Name: biz_aux_exam uq_biz_aux_exam_exam_no; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_aux_exam
    ADD CONSTRAINT uq_biz_aux_exam_exam_no UNIQUE (exam_no);


--
-- Name: biz_clinical_order uq_biz_clinical_order_order_no; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_clinical_order
    ADD CONSTRAINT uq_biz_clinical_order_order_no UNIQUE (order_no);


--
-- Name: biz_encounter uq_biz_encounter_encounter_no; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_encounter
    ADD CONSTRAINT uq_biz_encounter_encounter_no UNIQUE (encounter_no);


--
-- Name: biz_imaging_exam uq_biz_imaging_exam_exam_no; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_imaging_exam
    ADD CONSTRAINT uq_biz_imaging_exam_exam_no UNIQUE (exam_no);


--
-- Name: biz_lab_report uq_biz_lab_report_report_no; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_lab_report
    ADD CONSTRAINT uq_biz_lab_report_report_no UNIQUE (report_no);


--
-- Name: biz_patient_master uq_biz_patient_master_patient_id; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_patient_master
    ADD CONSTRAINT uq_biz_patient_master_patient_id UNIQUE (patient_id);


--
-- Name: biz_prescription uq_biz_prescription_prescription_no; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_prescription
    ADD CONSTRAINT uq_biz_prescription_prescription_no UNIQUE (prescription_no);


--
-- Name: biz_surgery_anesthesia uq_biz_surgery_anesthesia_surgery_no; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_surgery_anesthesia
    ADD CONSTRAINT uq_biz_surgery_anesthesia_surgery_no UNIQUE (surgery_no);


--
-- Name: biz_transfusion uq_biz_transfusion_transfusion_no; Type: CONSTRAINT; Schema: hospital_demo; Owner: -
--

ALTER TABLE ONLY hospital_demo.biz_transfusion
    ADD CONSTRAINT uq_biz_transfusion_transfusion_no UNIQUE (transfusion_no);


--
-- Name: api_call_log api_call_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_call_log
    ADD CONSTRAINT api_call_log_pkey PRIMARY KEY (id);


--
-- Name: api_client api_client_app_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_client
    ADD CONSTRAINT api_client_app_id_key UNIQUE (app_id);


--
-- Name: api_client api_client_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_client
    ADD CONSTRAINT api_client_pkey PRIMARY KEY (id);


--
-- Name: api_service api_service_api_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_service
    ADD CONSTRAINT api_service_api_code_key UNIQUE (api_code);


--
-- Name: api_service api_service_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_service
    ADD CONSTRAINT api_service_pkey PRIMARY KEY (id);


--
-- Name: asset asset_asset_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asset
    ADD CONSTRAINT asset_asset_code_key UNIQUE (asset_code);


--
-- Name: asset_domain asset_domain_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asset_domain
    ADD CONSTRAINT asset_domain_pkey PRIMARY KEY (id);


--
-- Name: asset asset_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asset
    ADD CONSTRAINT asset_pkey PRIMARY KEY (id);


--
-- Name: biz_patient biz_patient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.biz_patient
    ADD CONSTRAINT biz_patient_pkey PRIMARY KEY (id);


--
-- Name: data_source data_source_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_source
    ADD CONSTRAINT data_source_pkey PRIMARY KEY (id);


--
-- Name: data_standard data_standard_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_standard
    ADD CONSTRAINT data_standard_code_key UNIQUE (code);


--
-- Name: data_standard data_standard_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_standard
    ADD CONSTRAINT data_standard_pkey PRIMARY KEY (id);


--
-- Name: lineage_edge lineage_edge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lineage_edge
    ADD CONSTRAINT lineage_edge_pkey PRIMARY KEY (id);


--
-- Name: meta_column meta_column_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meta_column
    ADD CONSTRAINT meta_column_pkey PRIMARY KEY (id);


--
-- Name: meta_table meta_table_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meta_table
    ADD CONSTRAINT meta_table_pkey PRIMARY KEY (id);


--
-- Name: quality_result quality_result_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_result
    ADD CONSTRAINT quality_result_pkey PRIMARY KEY (id);


--
-- Name: quality_rule quality_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_rule
    ADD CONSTRAINT quality_rule_pkey PRIMARY KEY (id);


--
-- Name: sched_job sched_job_job_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sched_job
    ADD CONSTRAINT sched_job_job_code_key UNIQUE (job_code);


--
-- Name: sched_job_log sched_job_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sched_job_log
    ADD CONSTRAINT sched_job_log_pkey PRIMARY KEY (id);


--
-- Name: sched_job sched_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sched_job
    ADD CONSTRAINT sched_job_pkey PRIMARY KEY (id);


--
-- Name: security_level security_level_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_level
    ADD CONSTRAINT security_level_code_key UNIQUE (code);


--
-- Name: security_level security_level_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_level
    ADD CONSTRAINT security_level_pkey PRIMARY KEY (id);


--
-- Name: security_policy security_policy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_policy
    ADD CONSTRAINT security_policy_pkey PRIMARY KEY (id);


--
-- Name: standard_category standard_category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.standard_category
    ADD CONSTRAINT standard_category_pkey PRIMARY KEY (id);


--
-- Name: standard_mapping standard_mapping_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.standard_mapping
    ADD CONSTRAINT standard_mapping_pkey PRIMARY KEY (id);


--
-- Name: sys_user sys_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user
    ADD CONSTRAINT sys_user_pkey PRIMARY KEY (id);


--
-- Name: sys_user sys_user_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user
    ADD CONSTRAINT sys_user_username_key UNIQUE (username);


--
-- Name: meta_table uk_meta_table; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.meta_table
    ADD CONSTRAINT uk_meta_table UNIQUE (source_id, schema_name, table_name);


--
-- Name: idx_acr_asset; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_acr_asset ON dam_dam.asset_category_rel USING btree (asset_id);


--
-- Name: idx_acr_category; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_acr_category ON dam_dam.asset_category_rel USING btree (category_id);


--
-- Name: idx_acr_domain; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_acr_domain ON dam_dam.asset_category_rel USING btree (subject_domain_id);


--
-- Name: idx_asset_favorite_asset_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_asset_favorite_asset_id ON dam_dam.asset_favorite USING btree (asset_id);


--
-- Name: idx_asset_info_category_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_asset_info_category_id ON dam_dam.asset_info USING btree (category_id);


--
-- Name: idx_asset_rating_asset_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_asset_rating_asset_id ON dam_dam.asset_rating USING btree (asset_id);


--
-- Name: idx_asset_statistics_asset_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_asset_statistics_asset_id ON dam_dam.asset_statistics USING btree (asset_id);


--
-- Name: idx_asset_tag_rel_asset_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_asset_tag_rel_asset_id ON dam_dam.asset_tag_rel USING btree (asset_id);


--
-- Name: idx_asset_usage_log_asset_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_asset_usage_log_asset_id ON dam_dam.asset_usage_log USING btree (asset_id);


--
-- Name: idx_metadata_business_table_rel_table_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_business_table_rel_table_id ON dam_dam.metadata_business_table_rel USING btree (table_id);


--
-- Name: idx_metadata_collection_datasource_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_collection_datasource_id ON dam_dam.metadata_collection USING btree (datasource_id);


--
-- Name: idx_metadata_collection_schema_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_collection_schema_id ON dam_dam.metadata_collection USING btree (schema_id);


--
-- Name: idx_metadata_column_datasource_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_column_datasource_id ON dam_dam.metadata_column USING btree (datasource_id);


--
-- Name: idx_metadata_column_schema_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_column_schema_id ON dam_dam.metadata_column USING btree (schema_id);


--
-- Name: idx_metadata_column_table_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_column_table_id ON dam_dam.metadata_column USING btree (table_id);


--
-- Name: idx_metadata_destruction_record_target_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_destruction_record_target_id ON dam_dam.metadata_destruction_record USING btree (target_id);


--
-- Name: idx_metadata_destruction_record_target_type; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_destruction_record_target_type ON dam_dam.metadata_destruction_record USING btree (target_type);


--
-- Name: idx_metadata_lifecycle_policy_target_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_lifecycle_policy_target_id ON dam_dam.metadata_lifecycle_policy USING btree (target_id);


--
-- Name: idx_metadata_lifecycle_policy_target_type; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_lifecycle_policy_target_type ON dam_dam.metadata_lifecycle_policy USING btree (target_type);


--
-- Name: idx_metadata_lineage_collection_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_lineage_collection_id ON dam_dam.metadata_lineage USING btree (collection_id);


--
-- Name: idx_metadata_lineage_source_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_lineage_source_id ON dam_dam.metadata_lineage USING btree (source_id);


--
-- Name: idx_metadata_lineage_source_type; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_lineage_source_type ON dam_dam.metadata_lineage USING btree (source_type);


--
-- Name: idx_metadata_lineage_target_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_lineage_target_id ON dam_dam.metadata_lineage USING btree (target_id);


--
-- Name: idx_metadata_lineage_target_type; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_lineage_target_type ON dam_dam.metadata_lineage USING btree (target_type);


--
-- Name: idx_metadata_management_table_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_management_table_id ON dam_dam.metadata_management USING btree (table_id);


--
-- Name: idx_metadata_schema_datasource_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_schema_datasource_id ON dam_dam.metadata_schema USING btree (datasource_id);


--
-- Name: idx_metadata_table_datasource_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_table_datasource_id ON dam_dam.metadata_table USING btree (datasource_id);


--
-- Name: idx_metadata_table_schema_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_table_schema_id ON dam_dam.metadata_table USING btree (schema_id);


--
-- Name: idx_metadata_version_datasource_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_version_datasource_id ON dam_dam.metadata_version USING btree (datasource_id);


--
-- Name: idx_metadata_version_schema_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_version_schema_id ON dam_dam.metadata_version USING btree (schema_id);


--
-- Name: idx_metadata_version_table_id; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE INDEX idx_metadata_version_table_id ON dam_dam.metadata_version USING btree (table_id);


--
-- Name: uk_asset_domain_rel; Type: INDEX; Schema: dam_dam; Owner: -
--

CREATE UNIQUE INDEX uk_asset_domain_rel ON dam_dam.asset_category_rel USING btree (asset_id, subject_domain_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_report_catalog_status; Type: INDEX; Schema: dam_dan; Owner: -
--

CREATE INDEX idx_report_catalog_status ON dam_dan.report_catalog USING btree (status);


--
-- Name: uk_report_catalog_asset; Type: INDEX; Schema: dam_dan; Owner: -
--

CREATE UNIQUE INDEX uk_report_catalog_asset ON dam_dan.report_catalog USING btree (asset_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_report_catalog_code; Type: INDEX; Schema: dam_dan; Owner: -
--

CREATE UNIQUE INDEX uk_report_catalog_code ON dam_dan.report_catalog USING btree (report_code) WHERE (deleted_at IS NULL);


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
-- Name: uk_report_dimension_code; Type: INDEX; Schema: dam_dimension; Owner: -
--

CREATE UNIQUE INDEX uk_report_dimension_code ON dam_dimension.report_dimension USING btree (dim_code) WHERE (deleted_at IS NULL);


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
-- Name: idx_api_call_log_called_at; Type: INDEX; Schema: dam_service; Owner: -
--

CREATE INDEX idx_api_call_log_called_at ON dam_service.api_call_log USING btree (called_at);


--
-- Name: idx_api_service_asset_id; Type: INDEX; Schema: dam_service; Owner: -
--

CREATE INDEX idx_api_service_asset_id ON dam_service.api_service USING btree (asset_id);


--
-- Name: idx_biz_aux_exam_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_aux_exam_encounter_no ON hospital_demo.biz_aux_exam USING btree (encounter_no);


--
-- Name: idx_biz_aux_exam_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_aux_exam_patient_id ON hospital_demo.biz_aux_exam USING btree (patient_id);


--
-- Name: idx_biz_clinical_order_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_clinical_order_encounter_no ON hospital_demo.biz_clinical_order USING btree (encounter_no);


--
-- Name: idx_biz_clinical_order_order_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_clinical_order_order_no ON hospital_demo.biz_clinical_order USING btree (order_no);


--
-- Name: idx_biz_clinical_order_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_clinical_order_patient_id ON hospital_demo.biz_clinical_order USING btree (patient_id);


--
-- Name: idx_biz_diagnosis_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_diagnosis_encounter_no ON hospital_demo.biz_diagnosis USING btree (encounter_no);


--
-- Name: idx_biz_diagnosis_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_diagnosis_patient_id ON hospital_demo.biz_diagnosis USING btree (patient_id);


--
-- Name: idx_biz_emr_document_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_emr_document_encounter_no ON hospital_demo.biz_emr_document USING btree (encounter_no);


--
-- Name: idx_biz_emr_document_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_emr_document_patient_id ON hospital_demo.biz_emr_document USING btree (patient_id);


--
-- Name: idx_biz_encounter_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_encounter_encounter_no ON hospital_demo.biz_encounter USING btree (encounter_no);


--
-- Name: idx_biz_encounter_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_encounter_patient_id ON hospital_demo.biz_encounter USING btree (patient_id);


--
-- Name: idx_biz_encounter_transfer_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_encounter_transfer_encounter_no ON hospital_demo.biz_encounter_transfer USING btree (encounter_no);


--
-- Name: idx_biz_imaging_exam_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_imaging_exam_encounter_no ON hospital_demo.biz_imaging_exam USING btree (encounter_no);


--
-- Name: idx_biz_imaging_exam_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_imaging_exam_patient_id ON hospital_demo.biz_imaging_exam USING btree (patient_id);


--
-- Name: idx_biz_lab_report_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_lab_report_encounter_no ON hospital_demo.biz_lab_report USING btree (encounter_no);


--
-- Name: idx_biz_lab_report_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_lab_report_patient_id ON hospital_demo.biz_lab_report USING btree (patient_id);


--
-- Name: idx_biz_lab_report_report_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_lab_report_report_no ON hospital_demo.biz_lab_report USING btree (report_no);


--
-- Name: idx_biz_lab_result_report_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_lab_result_report_no ON hospital_demo.biz_lab_result USING btree (report_no);


--
-- Name: idx_biz_order_exec_order_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_order_exec_order_no ON hospital_demo.biz_order_exec USING btree (order_no);


--
-- Name: idx_biz_patient_allergy_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_patient_allergy_patient_id ON hospital_demo.biz_patient_allergy USING btree (patient_id);


--
-- Name: idx_biz_patient_master_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_patient_master_patient_id ON hospital_demo.biz_patient_master USING btree (patient_id);


--
-- Name: idx_biz_prescription_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_prescription_encounter_no ON hospital_demo.biz_prescription USING btree (encounter_no);


--
-- Name: idx_biz_prescription_item_prescription_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_prescription_item_prescription_no ON hospital_demo.biz_prescription_item USING btree (prescription_no);


--
-- Name: idx_biz_prescription_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_prescription_patient_id ON hospital_demo.biz_prescription USING btree (patient_id);


--
-- Name: idx_biz_prescription_prescription_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_prescription_prescription_no ON hospital_demo.biz_prescription USING btree (prescription_no);


--
-- Name: idx_biz_surgery_anesthesia_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_surgery_anesthesia_encounter_no ON hospital_demo.biz_surgery_anesthesia USING btree (encounter_no);


--
-- Name: idx_biz_surgery_anesthesia_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_surgery_anesthesia_patient_id ON hospital_demo.biz_surgery_anesthesia USING btree (patient_id);


--
-- Name: idx_biz_transfusion_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_transfusion_encounter_no ON hospital_demo.biz_transfusion USING btree (encounter_no);


--
-- Name: idx_biz_transfusion_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_transfusion_patient_id ON hospital_demo.biz_transfusion USING btree (patient_id);


--
-- Name: idx_biz_vital_signs_encounter_no; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_vital_signs_encounter_no ON hospital_demo.biz_vital_signs USING btree (encounter_no);


--
-- Name: idx_biz_vital_signs_patient_id; Type: INDEX; Schema: hospital_demo; Owner: -
--

CREATE INDEX idx_biz_vital_signs_patient_id ON hospital_demo.biz_vital_signs USING btree (patient_id);


--
-- Name: idx_acl_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_acl_time ON public.api_call_log USING btree (called_at);


--
-- Name: idx_asset_domain; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asset_domain ON public.asset USING btree (domain_id);


--
-- Name: idx_lineage_src; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lineage_src ON public.lineage_edge USING btree (source_asset_id);


--
-- Name: idx_lineage_tgt; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lineage_tgt ON public.lineage_edge USING btree (target_asset_id);


--
-- Name: idx_meta_column_table; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_meta_column_table ON public.meta_column USING btree (table_id);


--
-- Name: idx_qr_rule; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qr_rule ON public.quality_result USING btree (rule_id);


--
-- Name: idx_qr_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qr_time ON public.quality_result USING btree (run_at);


--
-- Name: idx_sjl_job; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sjl_job ON public.sched_job_log USING btree (job_id);


--
-- Name: idx_sp_asset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sp_asset ON public.security_policy USING btree (asset_id);


--
-- PostgreSQL database dump complete
--

