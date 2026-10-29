package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.catalog.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "metadata_collection", schema = "dam_metadata")
public class DamMetadataCollection {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 数据源ID */
    private Long datasourceId;
    /** Schema ID */
    private Long schemaId;
    /** 任务名称 */
    private String jobName;
    /** 任务类型 */
    private String jobType;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 采集总表数 */
    private Integer totalTables;
    /** 采集总字段数 */
    private Integer totalColumns;
    /** 成功数量 */
    private Integer successCount;
    /** 失败数量 */
    private Integer failCount;
    /** 错误日志 */
    private String errorMessage;
    /** 详细日志 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String detailLog;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
