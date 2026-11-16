package com.dam.taskcenter.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.taskcenter.entity.TaskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务日志 Mapper（dam_task.task_log）。
 *
 * @author DAM Platform
 */
@Mapper
public interface TaskLogMapper extends BaseMapper<TaskLog> {
}
