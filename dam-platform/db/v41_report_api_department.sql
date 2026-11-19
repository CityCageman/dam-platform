-- v41: 报表、数据 API 补 department，用于角色数据范围过滤
BEGIN;
ALTER TABLE IF EXISTS dam_report.report_catalog
    ADD COLUMN IF NOT EXISTS department varchar(100);
COMMENT ON COLUMN dam_report.report_catalog.department IS '报表归属部门；空表示全院/公共报表';

ALTER TABLE IF EXISTS dam_data_service.data_api
    ADD COLUMN IF NOT EXISTS department varchar(100);
COMMENT ON COLUMN dam_data_service.data_api.department IS '数据 API 归属部门；空表示全院/公共服务';

CREATE INDEX IF NOT EXISTS idx_report_catalog_department ON dam_report.report_catalog (department);
CREATE INDEX IF NOT EXISTS idx_data_api_department ON dam_data_service.data_api (department);
COMMIT;
