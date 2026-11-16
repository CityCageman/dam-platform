package com.dam.scheduler.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.scheduler.entity.ScheduleJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 查询所有已启用且未删除的调度任务，供自动派发扫描计算触发点。
     * <p>
     * 这里只做候选集读取，不加行锁；真正的派发在短事务内按任务 ID 使用
     * {@link #selectEnabledJobForUpdate(Long)} 重新加锁并校验，避免长时间持有锁。
     *
     * @return 已启用任务列表（按 ID 升序）
     */
    @Select("SELECT * FROM dam_scheduler.schedule_job "
            + "WHERE status = 'ENABLED' AND deleted_at IS NULL ORDER BY id")
    List<ScheduleJob> selectEnabledJobs();

    /**
     * 按 ID 锁定单个已启用任务（PostgreSQL 行锁）。
     * <p>
     * 使用 {@code FOR UPDATE SKIP LOCKED}：多实例同时扫描时，已被其他实例锁定的任务
     * 直接跳过，不会阻塞或重复派发；事务提交/回滚后锁自动释放。
     * 必须在事务中调用，并在拿到最新 {@code last_dispatched_at} 后重新判断是否到点。
     *
     * @param id 任务ID
     * @return 已启用且未删除的任务；行被其他事务锁定、任务不存在或非启用状态时返回 null
     */
    @Select("SELECT * FROM dam_scheduler.schedule_job "
            + "WHERE id = #{id} AND status = 'ENABLED' AND deleted_at IS NULL "
            + "FOR UPDATE SKIP LOCKED")
    ScheduleJob selectEnabledJobForUpdate(@Param("id") Long id);

    /**
     * 更新任务最近一次自动派发时间。
     * <p>
     * 仅自动派发成功后调用；条件中保留 status='ENABLED'，避免状态被并发修改后误写。
     *
     * @param id              任务ID
     * @param lastDispatchedAt 本次自动派发时间
     * @return 更新行数
     */
    @Update("UPDATE dam_scheduler.schedule_job SET last_dispatched_at = #{lastDispatchedAt} "
            + "WHERE id = #{id} AND status = 'ENABLED'")
    int updateLastDispatchedAt(@Param("id") Long id,
                               @Param("lastDispatchedAt") LocalDateTime lastDispatchedAt);
}
