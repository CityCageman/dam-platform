package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "source_system", schema = "dam_metadata")
public class DamSourceSystem {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 系统代码 */
    private String systemCode;
    /** 系统名称 */
    private String systemName;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // ===== 兼容字段（前端使用 code/name，Service 层返回时填充，不参与持久化） =====
    @TableField(exist = false)
    private String code;
    @TableField(exist = false)
    private String name;
    @TableField(exist = false)
    private Integer sortOrder;
}
