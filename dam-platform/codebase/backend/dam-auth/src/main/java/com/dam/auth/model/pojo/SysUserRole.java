package com.dam.auth.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联实体
 * <p>
 * 映射 dam_sys.sys_user_role。授权采用“全量替换”语义：先物理删除该用户的旧关联，
 * 再按新 roleIds 批量插入，保证关联关系不重复累积；历史通过操作日志追溯。
 * 查询时显式过滤 deleted_at，兼容历史逻辑删除数据。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "sys_user_role", schema = "dam_sys")
public class SysUserRole {

    /** 自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 角色ID */
    private Long roleId;

    /** 是否默认角色：Y-是，N-否 */
    private String defaultRole;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 更新人 */
    private String updatedBy;

    /** 删除时间（历史逻辑删除标记；本服务全量替换时物理删除） */
    private LocalDateTime deletedAt;
}
