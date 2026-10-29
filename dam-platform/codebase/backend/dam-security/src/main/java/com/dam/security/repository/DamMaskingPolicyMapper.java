package com.dam.security.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.security.entity.DamMaskingPolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 脱敏策略 Mapper（security_masking_policy 表）
 * <p>
 * 基于 MyBatis-Plus 提供脱敏策略表的基础 CRUD，
 * 由 {@link com.dam.security.service.SecurityService} 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamMaskingPolicyMapper extends BaseMapper<DamMaskingPolicy> {}
