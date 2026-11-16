package com.dam.catalog.system.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.system.entity.SysDictType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用字典类型 Mapper
 * <p>
 * 基于 MyBatis-Plus 提供 sys_dict_type 表的基础 CRUD，
 * 按 dictTypeCode 精确查询字典类型由 {@link com.dam.catalog.system.service.DictService} 封装。
 *
 * @author DAM Platform
 */
@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {
}
