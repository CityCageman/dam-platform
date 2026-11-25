package com.dam.governance.lineage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 来源系统（dam_dam.source_system，只读引用）
 * 用于血缘节点列表回填"所属系统"名称
 */
@Data
@TableName(value = "source_system", schema = "dam_dam")
public class SourceSystem {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 系统代码 */
    private String systemCode;
    /** 系统名称 */
    private String systemName;
}
