package com.dam.scheduler.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.scheduler.entity.ScheduleJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 调度任务 Mapper。
 *
 * @author DAM Platform
 */
@Mapper
public interface ScheduleJobMapper extends BaseMapper<ScheduleJob> {

    /**
     * 统计任务编码占用情况（包含已逻辑删除记录）。
     * <p>
     * job_code 是全局唯一索引，逻辑删除不会释放该编码，创建前需要连同已删除记录一起校验。
     *
     * @param jobCode 任务编码
     * @return 匹配记录数
     */
    @Select("SELECT COUNT(1) FROM dam_scheduler.schedule_job WHERE job_code = #{jobCode}")
    long countByCodeIncludingDeleted(@Param("jobCode") String jobCode);
}
