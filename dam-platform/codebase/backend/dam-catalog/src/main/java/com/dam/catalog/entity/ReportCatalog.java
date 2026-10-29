package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dam.catalog.config.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表资产扩展属性（dam_asset.report_catalog）
 * <p>
 * 与 asset_info 一对一（source_metadata_type='REPORT'），复用资产的收藏、评分、标签、搜索与主题域权限。
 */
@Data
@TableName(value = "report_catalog", schema = "dam_asset")
public class ReportCatalog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联资产ID（asset_info.id） */
    private Long assetId;

    /** 报表编码（唯一） */
    private String reportCode;

    /** 报表名称 */
    private String reportName;

    /** 报表类型（字典 report_type：REPORT/VIEW/PROCEDURE/ETL_JOB/ADHOC） */
    private String reportTypeId;

    /** 业务维度标签（JSONB 数组，如 ["科室","时间"]） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String dimensionCodes;

    /** 执行方式：SQL / PROC / HTTP / IFRAME / STATIC */
    private String execType;

    /** 数据源ID（SQL/PROC 执行时使用） */
    private Long dataSourceId;

    /** 查询语句（SQL）或存储过程调用语句（CALL xxx(?)） */
    private String sqlContent;

    /** 外部地址（HTTP/IFRAME 使用） */
    private String endpointUrl;

    /** 参数定义（JSONB 数组） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String paramDefs;

    /** 结果列定义（JSONB 数组） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String resultColumns;

    /** 封面图地址 */
    private String coverImage;

    /** 更新频率：DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY/REALTIME */
    private String updateFrequency;

    /** 负责人 */
    private String owner;

    private String description;

    private Long sortOrder;

    private Integer status;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
