package com.dam.platform.scheduler.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.platform.scheduler.entity.ScheduleDag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度 DAG 定义 Mapper。
 *
 * @author DAM Platform
 */
@Mapper
public interface ScheduleDagMapper extends BaseMapper<ScheduleDag> {
}
