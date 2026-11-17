-- v33: 数据服务 OpenAPI API Key 鉴权与调用审计
-- 说明：
--   1. data_api_key 仅保存 API Key 的 SHA-256 摘要与短前缀，不保存明文 Key；
--   2. data_api_call_log 记录开放 API 每次调用的鉴权方式、结果、耗时与错误摘要，
--      不记录 API Key、密码与请求参数。
CREATE SCHEMA IF NOT EXISTS dam_data_service;

-- ---------- 数据服务 API Key ----------
CREATE TABLE IF NOT EXISTS dam_data_service.data_api_key (
    id           bigserial PRIMARY KEY,
    key_prefix   varchar(16) NOT NULL,
    key_hash     varchar(64) NOT NULL UNIQUE,
    subscriber   varchar(100) NOT NULL,
    name         varchar(100) NOT NULL,
    status       varchar(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at   timestamp,
    last_used_at timestamp,
    created_at   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   varchar(100) NOT NULL DEFAULT 'system'
);

COMMENT ON TABLE dam_data_service.data_api_key IS '数据服务 API Key：仅保存 SHA-256 摘要与展示前缀';
COMMENT ON COLUMN dam_data_service.data_api_key.key_prefix IS 'Key 展示前缀（不含密钥主体）';
COMMENT ON COLUMN dam_data_service.data_api_key.key_hash IS 'API Key 的 SHA-256 十六进制摘要';
COMMENT ON COLUMN dam_data_service.data_api_key.status IS 'ACTIVE/REVOKED';

CREATE INDEX IF NOT EXISTS idx_data_api_key_subscriber_status
    ON dam_data_service.data_api_key (subscriber, status);

-- ---------- 开放 API 调用审计 ----------
CREATE TABLE IF NOT EXISTS dam_data_service.data_api_call_log (
    id            bigserial PRIMARY KEY,
    api_id        bigint NOT NULL,
    api_code      varchar(64) NOT NULL,
    subscriber    varchar(100),
    auth_type     varchar(16) NOT NULL,
    status        varchar(16) NOT NULL,
    duration_ms   bigint NOT NULL DEFAULT 0,
    row_count     integer,
    error_message varchar(500),
    created_at    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE dam_data_service.data_api_call_log IS '开放 API 调用审计：不含 API Key、密码与请求参数';
COMMENT ON COLUMN dam_data_service.data_api_call_log.api_id IS '数据 API ID；无法解析时为 0';
COMMENT ON COLUMN dam_data_service.data_api_call_log.auth_type IS 'API_KEY/TOKEN';
COMMENT ON COLUMN dam_data_service.data_api_call_log.status IS 'SUCCESS/FAILED';
COMMENT ON COLUMN dam_data_service.data_api_call_log.error_message IS '脱敏并截断到 500 字符的错误摘要';

CREATE INDEX IF NOT EXISTS idx_data_api_call_log_api_id_created_at
    ON dam_data_service.data_api_call_log (api_id, created_at);
CREATE INDEX IF NOT EXISTS idx_data_api_call_log_subscriber_created_at
    ON dam_data_service.data_api_call_log (subscriber, created_at);
