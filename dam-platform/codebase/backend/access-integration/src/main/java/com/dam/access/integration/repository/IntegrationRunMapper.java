package com.dam.access.integration.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.access.integration.entity.IntegrationRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据集成运行记录 Mapper
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 提供；额外提供同一任务内最大 run_no 查询。
 * 并发控制由 integration_job 行锁 + integration_run 活跃记录唯一索引共同保证。
 *
 * @author DAM Platform
 */
@Mapper
public interface IntegrationRunMapper extends BaseMapper<IntegrationRun> {

    /**
     * 查询指定任务已有的最大运行序号
     * <p>
     * 仅在 trigger 事务持有 integration_job 行锁时调用，保证同一任务的 run_no 串行分配。
     *
     * @param jobId 集成任务 ID
     * @return 最大 run_no；尚无运行记录时返回 0
     */
    @Select("SELECT COALESCE(MAX(run_no), 0) FROM dam_integration.integration_run "
            + "WHERE job_id = #{jobId}")
    Integer selectMaxRunNo(@Param("jobId") Long jobId);
}
