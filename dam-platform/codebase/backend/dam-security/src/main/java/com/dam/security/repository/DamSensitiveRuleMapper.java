package com.dam.security.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.security.entity.DamSensitiveRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感数据规则 Mapper（security_sensitive_rule 表）
 * <p>
 * 基于 MyBatis-Plus 提供敏感数据规则表的基础 CRUD，
 * 由安全分类分级/脱敏拦截等服务调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamSensitiveRuleMapper extends BaseMapper<DamSensitiveRule> {}
