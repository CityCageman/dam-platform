package com.dam.security.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.security.entity.DamAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据操作审计日志 Mapper（security_data_audit_log 表，只追加不可变）
 * <p>
 * 基于 MyBatis-Plus 提供数据操作审计日志表的基础 CRUD，
 * 由 {@link com.dam.security.service.SecurityService} 检索审计日志。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamAuditLogMapper extends BaseMapper<DamAuditLog> {}
