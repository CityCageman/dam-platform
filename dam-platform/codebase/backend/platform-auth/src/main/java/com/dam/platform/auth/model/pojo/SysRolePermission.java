package com.dam.platform.auth.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-权限关联实体
 * <p>
 * 映射 dam_dpm.sys_role_permission，仅用于角色权限查询（可选能力）。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "sys_role_permission", schema = "dam_dpm")
public class SysRolePermission {

    /** 自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID */
    private Long roleId;

    /** 权限ID */
    private Long permissionId;

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

    /** 删除时间（历史逻辑删除标记） */
    private LocalDateTime deletedAt;
}
