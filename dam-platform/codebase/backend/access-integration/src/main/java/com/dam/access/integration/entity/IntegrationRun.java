package com.dam.access.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据集成任务运行记录
 * <p>
 * 对应 dam_dap.integration_run。一次 trigger 生成一条运行记录：
 * 事务内以 PENDING 状态落库，线程池开始执行时置 RUNNING，执行结束后置
 * SUCCESS/FAILED，并回填读取/写入行数、起止时间与失败原因。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "integration_run", schema = "dam_dap")
public class IntegrationRun {

    /** 等待线程池执行 */
    public static final String STATUS_PENDING = "PENDING";
    /** 正在执行同步 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 同步成功完成 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 同步失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属集成任务 ID */
    private Long jobId;

    /** 同一任务内的运行序号，从 1 开始 */
    private Integer runNo;

    /** 运行状态：PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 从源端读取的行数 */
    private Long rowsRead;

    /** 已提交写入目标端的行数 */
    private Long rowsWritten;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 失败原因（已脱敏并截断到 2000 字符） */
    private String errorMessage;

    /** 运行记录创建时间 */
    private LocalDateTime createdAt;
}
