package com.dam.processing.develop.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.platform.common.config.JsonbTypeHandler;
import com.dam.processing.develop.entity.DevTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 数据开发任务 Mapper（dam_dev.dev_task）
 * <p>
 * 基础 CRUD 与分页由 MyBatis-Plus 提供，逻辑删除条件由 {@code @TableLogic} 自动追加。
 * 额外提供两个自定义 SQL：唯一性统计（包含已逻辑删除记录，与数据库唯一约束保持一致）
 * 以及更新场景下的行锁查询（保证并发内容变更时版本号递增不冲突）。
 *
 * @author DAM Platform
 */
@Mapper
public interface DevTaskMapper extends BaseMapper<DevTask> {

    /**
     * 统计指定任务编码的记录数（包含已逻辑删除的记录）
     * <p>
     * 自定义 SQL 不会被 MyBatis-Plus 追加 deleted_at IS NULL 条件。
     *
     * @param taskCode 任务编码
     * @return 记录数
     */
    @Select("SELECT COUNT(1) FROM dam_dev.dev_task WHERE task_code = #{taskCode}")
    long countByTaskCodeIncludingDeleted(@Param("taskCode") String taskCode);

    /**
     * 按 ID 查询未删除任务并加行锁（SELECT ... FOR UPDATE）
     * <p>
     * 用于更新/发布/下线等写操作，避免并发修改导致版本号重复或状态覆盖。
     * 必须在事务中调用，锁在事务提交或回滚后释放。
     *
     * @param id 任务 ID
     * @return 任务定义；不存在或已逻辑删除时返回 null
     */
    @Select("SELECT * FROM dam_dev.dev_task WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    @Results(id = "devTaskWithDatasourceConfig", value = {
            // 自定义 SQL 不会使用 MyBatis-Plus 的 autoResultMap，需显式指定 JSONB 处理器
            @Result(column = "datasource_config", property = "datasourceConfig",
                    typeHandler = JsonbTypeHandler.class)
    })
    DevTask selectByIdForUpdate(@Param("id") Long id);
}
