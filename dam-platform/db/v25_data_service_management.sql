-- v25: 数据服务 API 管理（数据 API 定义与订阅关系）
-- 说明：仅提供数据 API 元数据管理，不实现真实 API 网关运行时。
CREATE SCHEMA IF NOT EXISTS dam_data_service;

-- ---------- 数据 API 定义 ----------
CREATE TABLE IF NOT EXISTS dam_data_service.data_api (
    id              bigserial PRIMARY KEY,
    api_code        varchar(64) NOT NULL,
    api_name        varchar(200) NOT NULL,
    path            varchar(500) NOT NULL,
    method          varchar(16) NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'DRAFT',
    description     text,
    upstream_type   varchar(50),
    upstream_config jsonb,
    created_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      varchar(100) NOT NULL DEFAULT 'system',
    updated_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      varchar(100) NOT NULL DEFAULT 'system',
    deleted_at      timestamp,
    CONSTRAINT uk_data_api_api_code UNIQUE (api_code),
    CONSTRAINT ck_data_api_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE'))
);

COMMENT ON TABLE dam_data_service.data_api IS '数据 API 定义表：编码、路径、方法、发布状态与上游配置';
COMMENT ON COLUMN dam_data_service.data_api.upstream_config IS '上游配置（JSONB）';
COMMENT ON COLUMN dam_data_service.data_api.deleted_at IS '逻辑删除时间，NULL 表示未删除';

CREATE INDEX IF NOT EXISTS idx_data_api_api_code ON dam_data_service.data_api (api_code);
CREATE INDEX IF NOT EXISTS idx_data_api_status ON dam_data_service.data_api (status);

-- ---------- 数据 API 订阅 ----------
CREATE TABLE IF NOT EXISTS dam_data_service.api_subscription (
    id         bigserial PRIMARY KEY,
    api_id     bigint NOT NULL,
    subscriber varchar(100) NOT NULL,
    status     varchar(20) NOT NULL DEFAULT 'PENDING',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_api_subscription_api_subscriber UNIQUE (api_id, subscriber),
    CONSTRAINT ck_api_subscription_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'DISABLED'))
);

COMMENT ON TABLE dam_data_service.api_subscription IS '数据 API 订阅表：订阅方与授权状态';
COMMENT ON COLUMN dam_data_service.api_subscription.status IS 'PENDING/ACTIVE/REJECTED/DISABLED';

CREATE INDEX IF NOT EXISTS idx_api_subscription_api_id ON dam_data_service.api_subscription (api_id);
