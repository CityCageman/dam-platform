package com.dam.operations.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 工单评论
 */
@Data
@TableName(value = "ticket_comment", schema = "dam_ticket")
public class DamTicketComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 工单ID */
    private Long ticketId;
    /** 父评论ID */
    private Long parentCommentId;
    /** 评论内容 */
    private String content;
    /** 附件列表 */
    @TableField(typeHandler = com.dam.catalog.config.JsonbTypeHandler.class)
    private String attachments;
    /** @提及的用户ID列表 */
    private String mentionedUsers;
    /** 是否内部评论 */
    private Boolean isInternal;
    /** 评论人ID */
    private String commenterId;
    private Integer status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    // ===== 旧字段（新表不存在，保留兼容） =====
    @TableField(exist = false)
    private Long userId;
    @TableField(exist = false)
    private String username;
    @TableField(exist = false)
    private String attachmentUrl;
}
