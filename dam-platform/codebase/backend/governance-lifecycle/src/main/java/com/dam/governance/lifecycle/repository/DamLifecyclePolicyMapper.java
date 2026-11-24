package com.dam.governance.lifecycle.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.governance.lifecycle.entity.DamLifecyclePolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生命周期策略 Mapper（metadata_lifecycle_policy 表）
 * <p>
 * 基于 MyBatis-Plus 提供生命周期策略表的基础 CRUD，
 * 由 {@link com.dam.governance.lifecycle.service.LifecycleService} / StorageTieringService 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamLifecyclePolicyMapper extends BaseMapper<DamLifecyclePolicy> {}
