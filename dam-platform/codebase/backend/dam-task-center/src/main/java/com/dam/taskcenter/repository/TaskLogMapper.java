package com.dam.taskcenter.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.taskcenter.entity.TaskLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 任务日志 Mapper（dam_task.task_log）。
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 提供；额外提供按消息前缀查询最近一条日志，
 * 供回调服务读取以 CALLBACK_FINISH 开头的结构化结束标记（rowsRead/rowsWritten）。
 *
 * @author DAM Platform
 */
@Mapper
public interface TaskLogMapper extends BaseMapper<TaskLog> {

    /**
     * 查询指定实例下最近一条以指定前缀开头的日志。
     * <p>
     * 使用 LEFT(message, prefixLength) 精确匹配前缀，避免 LIKE 通配符影响。
     *
     * @param instanceId   实例ID
     * @param prefix       消息前缀
     * @param prefixLength 前缀长度
     * @return 最近一条匹配日志；不存在时返回 null
     */
    @Select("SELECT * FROM dam_task.task_log WHERE instance_id = #{instanceId} "
            + "AND LEFT(message, #{prefixLength}) = #{prefix} "
            + "ORDER BY id DESC LIMIT 1")
    TaskLog selectLatestByMessagePrefix(@Param("instanceId") Long instanceId,
                                        @Param("prefix") String prefix,
                                        @Param("prefixLength") int prefixLength);
}
