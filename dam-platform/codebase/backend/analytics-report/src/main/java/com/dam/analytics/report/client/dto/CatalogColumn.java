package com.dam.analytics.report.client.dto;

import lombok.Data;

/**
 * 技术元数据字段（DAM ReportData API 返回的报表生成上下文）。
 *
 * @author DAM Platform
 */
@Data
public class CatalogColumn {

    private Long id;
    private String columnName;
    private String columnType;
    private String columnComment;
    private Boolean isPrimaryKey;
    private Boolean isForeignKey;
    private String referenceTableName;
    private Integer ordinalPosition;
}
