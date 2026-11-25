package com.dam.governance.standard.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.governance.standard.entity.DamStandard;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标准码表 Mapper（standard_code_table 表，dam_dgp schema）
 * <p>
 * 基于 MyBatis-Plus 提供标准码表的基础 CRUD，
 * 由 {@link com.dam.governance.standard.service.StandardService} 调用。
 *
 * @author DAM Platform
 */
@Mapper
public interface DamStandardMapper extends BaseMapper<DamStandard> {
}
