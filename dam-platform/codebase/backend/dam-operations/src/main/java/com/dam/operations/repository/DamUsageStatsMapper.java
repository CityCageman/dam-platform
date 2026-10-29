package com.dam.operations.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.operations.entity.DamUsageStats;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产使用统计 Mapper（asset_statistics 表，dam_asset schema）
 * <p>
 * 基于 MyBatis-Plus 提供资产使用统计表的基础 CRUD，
 * 由运营看板/价值评估等服务调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamUsageStatsMapper extends BaseMapper<DamUsageStats> {}
