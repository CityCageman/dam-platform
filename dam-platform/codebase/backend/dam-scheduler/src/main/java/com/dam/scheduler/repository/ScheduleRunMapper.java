package com.dam.scheduler.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.scheduler.entity.ScheduleRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 调度任务运行记录 Mapper。
 *
 * @author DAM Platform
 */
@Mapper
public interface ScheduleRunMapper extends BaseMapper<ScheduleRun> {

    /**
     * 查询待轮询的 DolphinScheduler 运行记录（游标分页）。
     * <p>
     * 只取已回填 {@code dolphin_process_instance_id} 且仍为 RUNNING、ID 大于游标的记录，
     * 按 ID 升序有界返回。调用方在批尾推进游标并在扫尾时归零，避免长期运行的实例
     * 一直占据批次头部导致后续记录饿死；终态由调用方通过状态 CAS 回写，不依赖行锁。
     *
     * @param runStatus 运行状态（固定 RUNNING）
     * @param afterId   游标（上一批最后一条 ID），0 表示从头扫描
     * @param limit     单轮最大条数
     * @return 待轮询运行记录
     */
    @Select("SELECT * FROM dam_scheduler.schedule_run "
            + "WHERE run_status = #{runStatus} AND dolphin_process_instance_id IS NOT NULL "
            + "AND id > #{afterId} ORDER BY id LIMIT #{limit}")
    List<ScheduleRun> selectDolphinPollable(@Param("runStatus") String runStatus,
                                            @Param("afterId") long afterId,
                                            @Param("limit") int limit);
}
