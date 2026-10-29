package com.dam.quality.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.quality.entity.DamQualityRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 质量规则 Mapper（metadata_quality_rule 表）
 * <p>
 * 基于 MyBatis-Plus 提供质量规则表的基础 CRUD，
 * 由质量规则管理/检测服务调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamQualityRuleMapper extends BaseMapper<DamQualityRule> {}
