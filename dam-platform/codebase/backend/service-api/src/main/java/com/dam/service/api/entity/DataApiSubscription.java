package com.dam.service.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据 API 订阅
 * <p>
 * 对应 {@code dam_data_service.api_subscription}：记录订阅方对某个数据 API 的
 * 订阅关系与审批状态。同一 API 与订阅方之间只允许存在一条记录。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "api_subscription", schema = "dam_data_service")
public class DataApiSubscription {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被订阅的数据 API ID */
    private Long apiId;

    /** 订阅方（应用/系统标识） */
    private String subscriber;

    /** 订阅状态：PENDING-待审批、ACTIVE-已生效、REJECTED-已拒绝、DISABLED-已禁用 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
