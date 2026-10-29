package com.dam.standard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "standard_code_table", schema = "dam_standard")
public class DamStandard {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 码表代码 */
    private String codeTableCode;
    /** 码表名称 */
    private String codeTableName;
    /** 码表类型 */
    private String codeTableType;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private String stdName;
    @TableField(exist = false)
    private String stdCode;
    @TableField(exist = false)
    private String stdType;
    @TableField(exist = false)
    private String category;
    @TableField(exist = false)
    private String definition;
    @TableField(exist = false)
    private String version;
    @TableField(exist = false)
    private Long approverId;
    @TableField(exist = false)
    private LocalDateTime publishDate;
}
