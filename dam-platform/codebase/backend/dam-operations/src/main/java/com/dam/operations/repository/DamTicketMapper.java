package com.dam.operations.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.operations.entity.DamTicket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单 Mapper（ticket_order 表）
 * <p>
 * 基于 MyBatis-Plus 提供工单表的基础 CRUD，
 * 由 {@link com.dam.operations.service.OperationsService} 等运营服务调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamTicketMapper extends BaseMapper<DamTicket> {}
