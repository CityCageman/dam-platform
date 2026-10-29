package com.dam.operations.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.operations.entity.DamTicketComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单评论 Mapper（ticket_comment 表）
 * <p>
 * 基于 MyBatis-Plus 提供工单评论表的基础 CRUD，
 * 由 {@link com.dam.operations.service.OperationsService} 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamTicketCommentMapper extends BaseMapper<DamTicketComment> {
}
