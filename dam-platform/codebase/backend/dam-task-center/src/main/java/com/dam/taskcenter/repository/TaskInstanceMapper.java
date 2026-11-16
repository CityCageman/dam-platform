package com.dam.taskcenter.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.taskcenter.entity.TaskInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 任务实例 Mapper（dam_task.task_instance）。
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 提供；额外提供同一任务定义内最大 run_no 查询，
 * 供触发接口生成下一个运行序号。
 *
 * @author DAM Platform
 */
@Mapper
public interface TaskInstanceMapper extends BaseMapper<TaskInstance> {

    /**
     * 查询指定任务定义下已有的最大 run_no。
     *
     * @param definitionId 任务定义ID
     * @return 最大 run_no；尚无实例时返回 0
     */
    @Select("SELECT COALESCE(MAX(run_no), 0) FROM dam_task.task_instance "
            + "WHERE definition_id = #{definitionId}")
    Integer selectMaxRunNo(@Param("definitionId") Long definitionId);
}
