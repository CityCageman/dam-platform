package com.dam.governance.standard.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.governance.standard.entity.DamStandardMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标准映射 Mapper（standard_mapping 表）
 * <p>
 * 基于 MyBatis-Plus 提供标准映射表的基础 CRUD，
 * 由 {@link com.dam.governance.standard.service.StandardService} 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamStandardMappingMapper extends BaseMapper<DamStandardMapping> {
}
