package com.dam.platform.taskcenter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务日志。
 * <p>
 * 对应 {@code dam_dpm.task_log}：按实例追加的执行日志，供执行器与运维排查使用。
 * 本服务只负责日志持久化，不解析日志内容。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "task_log", schema = "dam_dpm")
public class TaskLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务实例ID */
    private Long instanceId;

    /** 日志级别：TRACE/DEBUG/INFO/WARN/ERROR */
    private String logLevel;

    /** 日志内容 */
    private String message;

    /** 日志记录时间 */
    private LocalDateTime loggedAt;
}
