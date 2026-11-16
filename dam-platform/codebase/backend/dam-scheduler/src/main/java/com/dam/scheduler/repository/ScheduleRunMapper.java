package com.dam.scheduler.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.scheduler.entity.ScheduleRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度任务运行记录 Mapper。
 *
 * @author DAM Platform
 */
@Mapper
public interface ScheduleRunMapper extends BaseMapper<ScheduleRun> {
}
