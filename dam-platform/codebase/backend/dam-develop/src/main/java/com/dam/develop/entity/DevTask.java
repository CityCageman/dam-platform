package com.dam.develop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据开发任务定义
 * <p>
 * 对应 {@code dam_dev.dev_task}：保存 SQL/Python/Shell 任务的编码、类型、内容、
 * 数据源与发布状态。{@code current_version} 与 {@code dev_task_version} 中的
 * 最新版本号保持一致；{@code deleted_at} 使用 MyBatis-Plus 逻辑删除，
 * 未删除为 NULL，删除时置为当前时间，不物理删除历史版本。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "dev_task", schema = "dam_dev")
public class DevTask {

    /** 任务草稿状态 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 任务已发布状态 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 任务已下线状态 */
    public static final String STATUS_OFFLINE = "OFFLINE";

    /** 任务类型：SQL */
    public static final String TYPE_SQL = "SQL";
    /** 任务类型：Python */
    public static final String TYPE_PYTHON = "PYTHON";
    /** 任务类型：Shell */
    public static final String TYPE_SHELL = "SHELL";

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编码（全局唯一，创建后不允许修改） */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 任务类型：SQL/PYTHON/SHELL */
    private String taskType;

    /** 关联数据源 ID，可为空（由数据开发侧自行绑定） */
    private Long datasourceId;

    /** 任务脚本内容（SQL/Python/Shell 源码） */
    private String content;

    /** 发布状态：DRAFT/PUBLISHED/OFFLINE */
    private String status;

    /** 当前版本号，创建时为 1，内容变更时递增 */
    private Integer currentVersion;

    /** 任务描述 */
    private String description;

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
