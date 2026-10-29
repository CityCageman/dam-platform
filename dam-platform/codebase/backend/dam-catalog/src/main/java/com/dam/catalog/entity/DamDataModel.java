package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "model_domain", schema = "dam_model")
public class DamDataModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 主题域代码 */
    private String domainCode;
    /** 主题域名称 */
    private String domainName;
    /** 父主题域ID */
    private Long parentId;
    private String description;
    /** 排序序号 */
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // ===== 兼容字段（前端使用 modelName/modelCode，Service 层转换填充，不参与持久化） =====
    @TableField(exist = false)
    private String modelName;
    @TableField(exist = false)
    private String modelCode;
}
