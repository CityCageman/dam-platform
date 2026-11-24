package com.dam.access.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dam.common.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据集成任务实体
 * <p>
 * 对应 dam_integration.integration_job 表。任务保存源端/目标端配置与生命周期状态；
 * trigger 接口会创建 integration_run 并通过线程池执行真实 JDBC 全量同步。
 * source_config / target_config 为 PostgreSQL JSONB。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "integration_job", schema = "dam_integration", autoResultMap = true)
public class IntegrationJob {

    /** 任务草稿状态 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 任务已启用状态 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 任务已停用状态 */
    public static final String STATUS_DISABLED = "DISABLED";
    /** 任务运行中状态（真实同步执行期间由执行器设置） */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 任务失败状态 */
    public static final String STATUS_FAILED = "FAILED";

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编码（全局唯一，创建后不允许修改） */
    private String jobCode;

    /** 任务名称 */
    private String jobName;

    /** 源端类型，如 JDBC/CDC/FILE/API/KAFKA */
    private String sourceType;

    /** 源端配置（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String sourceConfig;

    /** 目标端类型，如 JDBC/FILE/KAFKA */
    private String targetType;

    /** 目标端配置（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String targetConfig;

    /** 同步模式，如 FULL/INCREMENTAL */
    private String syncMode;

    /** 任务状态：DRAFT/ENABLED/DISABLED/RUNNING/FAILED */
    private String status;

    /** 最近一次触发/开始运行时间 */
    private LocalDateTime lastRunAt;

    /** 最近一次失败原因 */
    private String lastError;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 更新人 */
    private String updatedBy;

    /** 逻辑删除时间，NULL 表示未删除 */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
