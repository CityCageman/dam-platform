package com.dam.access.integration.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.access.integration.entity.IntegrationJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 数据集成任务 Mapper
 * <p>
 * 基于 MyBatis-Plus 提供 integration_job 的基础 CRUD 与分页查询；
 * {@link #countByJobCodeIncludingDeleted(String)} 用于唯一性校验时把已逻辑删除的任务也纳入判断，
 * 与数据库上的 job_code 唯一索引保持一致；{@link #lockById(Long)} 用于 trigger 事务内
 * 串行化同一任务的运行记录创建。
 *
 * @author DAM Platform
 */
@Mapper
public interface IntegrationJobMapper extends BaseMapper<IntegrationJob> {

    /**
     * 统计指定任务编码的记录数（包含已逻辑删除的记录）
     * <p>
     * 自定义 SQL 不会被 MyBatis-Plus 追加 deleted_at IS NULL 条件。
     *
     * @param jobCode 任务编码
     * @return 记录数
     */
    @Select("SELECT COUNT(1) FROM dam_integration.integration_job WHERE job_code = #{jobCode}")
    long countByJobCodeIncludingDeleted(@Param("jobCode") String jobCode);

    /**
     * 对未删除的集成任务行加排他锁
     * <p>
     * 仅用于 trigger 事务内串行分配 run_no，并保证“同一任务不允许并发执行”的检查
     * 与 PENDING 记录插入处于同一临界区；事务提交后自动释放。
     *
     * @param id 任务 ID
     * @return 任务 ID；记录不存在或已逻辑删除时返回 null
     */
    @Select("SELECT id FROM dam_integration.integration_job "
            + "WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
