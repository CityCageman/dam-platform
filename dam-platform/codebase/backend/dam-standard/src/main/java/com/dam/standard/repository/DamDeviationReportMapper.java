package com.dam.standard.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.standard.entity.DamDeviationReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标准偏差报告 Mapper（standard_deviation_report 表）
 * <p>
 * 基于 MyBatis-Plus 提供标准偏差报告表的基础 CRUD，
 * 由 {@link com.dam.standard.service.StandardService} / StandardAutoValidatorService 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamDeviationReportMapper extends BaseMapper<DamDeviationReport> {
}
