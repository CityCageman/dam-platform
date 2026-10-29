package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dam.catalog.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "metadata_version", schema = "dam_metadata")
public class DamMetadataVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 数据源ID */
    private Long datasourceId;
    /** Schema ID */
    private Long schemaId;
    /** 表ID */
    private Long tableId;
    /** 版本号 */
    private String versionNumber;
    /** 变更类型 */
    private String changeType;
    /** 变更摘要 */
    private String changeSummary;
    /** 版本快照 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String snapshotData;
    /** 差异摘要 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String diffSummary;
    /** 变更人 */
    private String changedBy;
    /** 变更时间 */
    private LocalDateTime changedAt;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
