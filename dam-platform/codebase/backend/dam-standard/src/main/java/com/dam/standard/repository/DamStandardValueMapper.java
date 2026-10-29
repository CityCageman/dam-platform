package com.dam.standard.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.standard.entity.DamStandardValue;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标准码值 Mapper（standard_code_value 表）
 * <p>
 * 基于 MyBatis-Plus 提供标准码值表的基础 CRUD，
 * 由 {@link com.dam.standard.service.StandardService} 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamStandardValueMapper extends BaseMapper<DamStandardValue> {
}
