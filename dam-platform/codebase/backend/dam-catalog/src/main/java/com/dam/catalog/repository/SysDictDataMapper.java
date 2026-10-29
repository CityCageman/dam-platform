package com.dam.catalog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.catalog.entity.SysDictData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用字典数据 Mapper
 * <p>
 * 基于 MyBatis-Plus 提供 sys_dict_data 表的基础 CRUD，
 * 按字典类型ID查询启用字典项由 {@link com.dam.catalog.service.DictService} 封装。
 *
 * @author DAM Platform
 */
@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictData> {
}
