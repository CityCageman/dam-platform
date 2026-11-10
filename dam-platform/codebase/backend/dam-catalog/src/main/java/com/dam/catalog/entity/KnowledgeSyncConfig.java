package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "knowledge_sync_config", schema = "dam_asset")
public class KnowledgeSyncConfig {
    @TableId
    private Long id;
    private String externalKbId;
    private String externalKbName;
    private Integer autoSync;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
