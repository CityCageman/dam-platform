package com.dam.security.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.security.entity.DamDataPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * ABAC 数据权限 Mapper（security_data_permission 表）
 * <p>
 * 基于 MyBatis-Plus 提供 ABAC 数据权限表的基础 CRUD，
 * 由 {@link com.dam.security.service.AbacPermissionService} 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamDataPermissionMapper extends BaseMapper<DamDataPermission> {
}
