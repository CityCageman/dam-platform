package com.dam.analytics.report.client.dto;

import lombok.Data;

import java.util.List;

/**
 * 技术元数据表（DAM ReportData API 返回的报表生成上下文）。
 * <p>
 * 报表模块不再依赖 asset-catalog 的 MetadataTable 实体，字段按接口契约裁剪。
 *
 * @author DAM Platform
 */
@Data
public class CatalogTable {

    private Long id;
    private Long dataSourceId;
    private String schemaName;
    private String tableName;
    private String tableComment;
    private String subjectDomain;
    private List<CatalogColumn> columns;
}
