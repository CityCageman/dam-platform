package com.dam.platform.auth.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色实体
 * <p>
 * 映射 dam_dpm.sys_role。角色通过 sys_user_role 关联用户、通过 sys_role_menu 关联菜单；
 * 删除采用 deleted_at 逻辑删除，唯一性由代码校验（未删除记录内 role_code 唯一）。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "sys_role", schema = "dam_dpm")
public class SysRole {

    /** 自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码（未删除记录内唯一） */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色描述 */
    private String description;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 数据范围：ALL-全部，DEPARTMENT-本部门，SELF-仅本人 */
    private String dataScope;

    /** 默认驾驶舱编码：super/executive/department/ops/developer/governance/business/security */
    private String defaultCockpit;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 更新人 */
    private String updatedBy;

    /**
     * 删除时间。
     * <p>
     * 逻辑删除：未删除为 NULL，删除时置 now()；deleted_at 为 TIMESTAMP 列，
     * 不能使用默认的 0/1 删除值比较。
     */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
