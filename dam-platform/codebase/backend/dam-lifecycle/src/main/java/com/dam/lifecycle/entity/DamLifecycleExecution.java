package com.dam.lifecycle.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 生命周期策略执行记录
 */
@Data
@TableName(value = "metadata_lifecycle_execution", schema = "dam_metadata")
public class DamLifecycleExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long policyId;

    private String policyName;

    private Long assetId;

    private String assetName;

    /** 分层：热数据/温数据/冷数据/过期 */
    private String tier;

    /** 动作：archive/destroy/notify */
    private String action;

    private String reason;

    private LocalDateTime executedAt;

    private String executedBy;
}
