package com.dam.standard.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "standard_code_value", schema = "dam_standard")
public class DamStandardValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属码表ID */
    private Long codeTableId;
    /** 码值 */
    private String codeValue;
    /** 码值名称 */
    private String codeName;
    /** 父码值ID */
    private Long parentCodeId;
    private Integer sortOrder;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Long stdId;
    @TableField(exist = false)
    private String valueCode;
    @TableField(exist = false)
    private String valueName;
}
