package com.dam.service.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dam.platform.common.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据 API 定义
 * <p>
 * 对应 {@code dam_data_service.data_api}：描述可对外提供的数据 API 的编码、
 * 名称、访问路径、HTTP 方法、发布状态与上游配置。{@code deleted_at} 使用
 * MyBatis-Plus 逻辑删除（未删除为 NULL，删除时置 now()），避免物理删除元数据。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "data_api", schema = "dam_data_service", autoResultMap = true)
public class DataApi {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** API 编码（全局唯一，创建后不可修改） */
    private String apiCode;

    /** API 名称 */
    private String apiName;

    /** API 访问路径 */
    private String path;

    /** HTTP 方法（GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS） */
    private String method;

    /** 发布状态：DRAFT-草稿、PUBLISHED-已发布、OFFLINE-已下线 */
    private String status;

    /** API 描述 */
    private String description;

    /** 归属部门，用于数据范围过滤 */
    private String department;

    /** 上游类型（如 HTTP、SQL、MOCK） */
    private String upstreamType;

    /** 上游配置（PostgreSQL jsonb，使用 dam-common 的 JSONB 类型处理器） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String upstreamConfig;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 更新人 */
    private String updatedBy;

    /** 逻辑删除时间（NULL 表示未删除） */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
