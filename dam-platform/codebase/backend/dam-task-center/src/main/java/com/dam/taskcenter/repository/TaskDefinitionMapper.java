package com.dam.taskcenter.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.taskcenter.entity.TaskDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 任务定义 Mapper（dam_task.task_definition）。
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 提供；{@code @TableLogic} 自动追加未删除条件。
 * 额外提供包含已逻辑删除记录的唯一性统计，以及触发时用于串行分配 run_no 的
 * 行锁查询。
 *
 * @author DAM Platform
 */
@Mapper
public interface TaskDefinitionMapper extends BaseMapper<TaskDefinition> {

    /**
     * 统计指定 taskCode 的记录数（包含已逻辑删除的记录）。
     * <p>
     * 数据库唯一约束不区分逻辑删除状态，因此创建任务前必须按此口径校验，
     * 避免复用已删除编码时触发唯一约束异常。
     *
     * @param taskCode 任务编码
     * @return 记录数
     */
    @Select("SELECT COUNT(1) FROM dam_task.task_definition WHERE task_code = #{taskCode}")
    long countByTaskCodeIncludingDeleted(@Param("taskCode") String taskCode);

    /**
     * 对未删除的任务定义行加排他锁。
     * <p>
     * 仅用于触发事务内串行分配 run_no，事务提交后自动释放。
     *
     * @param id 任务定义ID
     * @return 任务定义ID；记录不存在或已逻辑删除时返回 null
     */
    @Select("SELECT id FROM dam_task.task_definition "
            + "WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
