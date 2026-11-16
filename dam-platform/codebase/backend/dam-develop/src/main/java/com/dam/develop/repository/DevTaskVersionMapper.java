package com.dam.develop.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.develop.entity.DevTaskVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据开发任务版本 Mapper（dam_dev.dev_task_version）
 * <p>
 * 版本记录只追加、不修改，由 {@link DevTaskMapper} 所在事务统一提交；基础 CRUD
 * 与条件查询由 MyBatis-Plus 提供。
 *
 * @author DAM Platform
 */
@Mapper
public interface DevTaskVersionMapper extends BaseMapper<DevTaskVersion> {
}
