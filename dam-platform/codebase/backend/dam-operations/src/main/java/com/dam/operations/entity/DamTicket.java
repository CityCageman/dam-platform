package com.dam.operations.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "ticket_order", schema = "dam_ticket")
public class DamTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 工单编号 */
    private String ticketNo;
    /** 工单类型 */
    private String ticketType;
    /** 工单标题 */
    private String title;
    private String description;
    /** 优先级 */
    private String priority;
    /** 申请人ID */
    private Long applicantId;
    /** 处理人ID */
    private Long assigneeId;
    /** 关联资产ID */
    private Long relatedAssetId;
    /** 关联申请单ID */
    private Long relatedApplicationId;
    /** 期望完成时间 */
    private LocalDateTime expectedCompleteTime;
    /** 实际完成时间 */
    private LocalDateTime actualCompleteTime;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Long creatorId;
    @TableField(exist = false)
    private LocalDateTime slaDeadline;
    @TableField(exist = false)
    private LocalDateTime closedAt;
}
