package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.catalog.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 扫描作业（承载于 metadata_collection 采集记录表）
 * 新库无 dam_scan_job 表，扫描作业信息通过 metadata_collection 持久化
 */
@Data
@TableName(value = "metadata_collection", schema = "dam_metadata")
public class DamScanJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 数据源ID（metadata_collection 真实列为 datasource_id，无下划线） */
    @TableField("datasource_id")
    private Long dataSourceId;
    /** Schema ID（metadata_collection.schema_id NOT NULL，无归属时用 0 兑底） */
    @TableField("schema_id")
    private Long schemaId;
    /** 任务名称（承载 databaseName） */
    @TableField("job_name")
    private String databaseName;
    /** 任务类型（承载 sourceType） */
    @TableField("job_type")
    private String sourceType;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 扫描表数量 */
    @TableField("total_tables")
    private Integer tableCount;
    /** 扫描字段数量 */
    @TableField("total_columns")
    private Integer fieldCount;
    /** 扫描Schema数量（承载到成功数） */
    @TableField("success_count")
    private Integer schemaCount;
    /** 失败数量 */
    @TableField("fail_count")
    private Integer failCount;
    /** 错误日志 */
    @TableField("error_message")
    private String errorMsg;
    /** 详细日志（承载快照 JSON） */
    @TableField(value = "detail_log", typeHandler = JsonbTypeHandler.class)
    private String snapshotJson;
    /** 变更记录（承载到快照，兼容旧接口） */
    @TableField(exist = false)
    private String changesJson;
    /** 操作人ID */
    @TableField(exist = false)
    private Long operatorId;
    /** 状态: 0-进行中 1-成功 2-失败 */
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
