package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "sys_config", schema = "dam_sys")
public class DamConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configType;
    private String configCode;
    private String configName;
    private String configValue;
    private String description;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
