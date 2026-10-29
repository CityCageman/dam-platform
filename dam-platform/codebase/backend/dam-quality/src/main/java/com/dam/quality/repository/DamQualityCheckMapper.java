package com.dam.quality.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.quality.entity.DamQualityCheck;
import org.apache.ibatis.annotations.Mapper;

/**
 * 质量检测结果 Mapper（metadata_quality_check_result 表）
 * <p>
 * 基于 MyBatis-Plus 提供质量检测结果表的基础 CRUD，
 * 由质量检测/报表服务调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamQualityCheckMapper extends BaseMapper<DamQualityCheck> {}
