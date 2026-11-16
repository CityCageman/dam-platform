package com.dam.taskcenter.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dam.taskcenter.entity.TaskInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务实例 Mapper（dam_task.task_instance）。
 * <p>
 * 基础 CRUD 由 MyBatis-Plus 提供；额外提供同一任务定义内最大 run_no 查询、
 * 回调结束事务内按主键加行锁的实例查询，以及超时实例回收所需的
 * 候选行加锁扫描与条件更新。
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

    /**
     * 按主键查询任务实例并加排他行锁。
     * <p>
     * 仅用于回调结束事务内串行化同一实例的并发状态回填，事务提交后自动释放。
     *
     * @param id 实例ID
     * @return 任务实例；不存在时返回 null
     */
    @Select("SELECT * FROM dam_task.task_instance WHERE id = #{id} FOR UPDATE")
    TaskInstance selectByIdForUpdate(@Param("id") Long id);

    /**
     * 扫描创建后长时间未开始的 PENDING 实例并加行锁。
     * <p>
     * 使用 PostgreSQL {@code FOR UPDATE SKIP LOCKED}：多实例部署时各节点只锁定自己
     * 扫描到的行，其他节点跳过已锁行，因此不会重复回收同一条实例。
     * 必须在事务内调用，锁随事务提交/回滚释放。
     *
     * @param cutoff 创建时间截止点（早于该时间视为超时）
     * @param limit  单次最多扫描条数
     * @return 超时候选实例ID（按创建时间升序）
     */
    @Select("SELECT id FROM dam_task.task_instance "
            + "WHERE status = 'PENDING' AND created_at < #{cutoff} "
            + "ORDER BY created_at, id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<Long> selectTimeoutPendingIds(@Param("cutoff") LocalDateTime cutoff,
                                       @Param("limit") int limit);

    /**
     * 扫描长时间未结束的 RUNNING 实例并加行锁。
     * <p>
     * 计时起点为 started_at，为空时回退到 created_at（COALESCE），
     * 与“执行器未回填 started_at 的实例按创建时间计时”的约定一致。
     * 同样使用 {@code FOR UPDATE SKIP LOCKED} 保证多实例下同一条实例只被一个节点锁定。
     *
     * @param cutoff 计时截止点（早于该时间视为超时）
     * @param limit  单次最多扫描条数
     * @return 超时候选实例ID
     */
    @Select("SELECT id FROM dam_task.task_instance "
            + "WHERE status = 'RUNNING' AND COALESCE(started_at, created_at) < #{cutoff} "
            + "ORDER BY COALESCE(started_at, created_at), id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<Long> selectTimeoutRunningIds(@Param("cutoff") LocalDateTime cutoff,
                                       @Param("limit") int limit);

    /**
     * 按“当前状态仍为期望状态”条件回收单条超时实例。
     * <p>
     * 条件更新是行锁之外的第二道并发保护：只有调用方仍看到 PENDING/RUNNING 时才会
     * 写入 CANCELLED/FAILED 与 finished_at/error_message；状态已被执行器或回调推进
     * 为终态时返回 0，调用方跳过即可，不会覆盖真实结果。
     *
     * @param id             实例ID
     * @param expectedStatus 期望的当前状态（PENDING/RUNNING）
     * @param targetStatus   回收后的状态（CANCELLED/FAILED，禁止 SUCCESS）
     * @param finishedAt     结束时间
     * @param errorMessage   回收原因
     * @return 受影响行数（0 表示状态已变化，未回收）
     */
    @Update("UPDATE dam_task.task_instance "
            + "SET status = #{targetStatus}, finished_at = #{finishedAt}, "
            + "error_message = #{errorMessage} "
            + "WHERE id = #{id} AND status = #{expectedStatus}")
    int recoverTimeoutById(@Param("id") Long id,
                           @Param("expectedStatus") String expectedStatus,
                           @Param("targetStatus") String targetStatus,
                           @Param("finishedAt") LocalDateTime finishedAt,
                           @Param("errorMessage") String errorMessage);
}
