package com.dam.lineage.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.lineage.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 报表SQL解析结果表（metadata_report_sql_parser）
 * <p>
 * 存储报表/视图的原始 SQL 与结构化解析结果，便于应用层血缘追溯与自动化采集。
 * sql_hash 用于去重，解析状态机 PENDING→PARSING→SUCCESS/FAILED。
 */
@Data
@TableName(value = "metadata_report_sql_parser", schema = "dam_metadata")
public class MetadataReportSqlParser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报表节点ID（metadata_lineage_source.id，REPORT/VIEW 类型节点） */
    private Long reportSourceId;

    /** 报表名称 */
    private String reportName;

    /** 报表类型（report_type 字典：REPORT/VIEW/PROCEDURE/ETL_JOB/ADHOC） */
    private String reportTypeId;

    /** 原始SQL内容 */
    private String sqlContent;

    /** SQL哈希（去重用，SHA-256） */
    private String sqlHash;

    /** 解析出的表列表（JSONB：[{schema, table, alias, type}]） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String parsedTables;

    /** 解析出的字段列表（JSONB：[{column, sourceTable, alias}]） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String parsedColumns;

    /** JOIN 关系（JSONB：[{fromTable, fromColumn, toTable, toColumn, joinType}]） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String joinRelations;

    /** 过滤条件（JSONB：["condition1", ...]） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String filterConditions;

    /** 分组字段（JSONB：["field1", ...]） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String groupByFields;

    /** 排序字段（JSONB：["field1 ASC", ...]） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String orderByFields;

    /** 计算字段（JSONB：[{alias, expression, calcType, dependsOn[]}]） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String calculatedFields;

    /** 解析状态（lineage_parse_status 字典：PENDING/PARSING/SUCCESS/FAILED） */
    private String parseStatusId;

    /** 解析失败原因 */
    private String parseError;

    /** 解析时间 */
    private LocalDateTime parseTime;

    /** 解析器版本 */
    private String parserVersion;

    /** 关联采集记录ID */
    private Long collectionId;

    /** 是否自动更新（扫描器/定时任务更新时置 true） */
    private Boolean isAutoUpdated;

    /** 描述 */
    private String description;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
