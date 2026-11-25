package com.dam.platform.operations.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.platform.operations.entity.DamUsageStats;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产使用统计 Mapper（asset_statistics 表，dam_dam schema）
 * <p>
 * 基于 MyBatis-Plus 提供资产使用统计表的基础 CRUD，
 * 由运营看板/价值评估等服务调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamUsageStatsMapper extends BaseMapper<DamUsageStats> {}
