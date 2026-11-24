package com.dam.platform.auth.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-菜单关联实体
 * <p>
 * 映射 dam_sys.sys_role_menu。角色菜单授权采用“全量替换”语义：
 * 先物理删除该角色的旧关联，再按新 menuIds 批量插入。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "sys_role_menu", schema = "dam_sys")
public class SysRoleMenu {

    /** 自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID */
    private Long roleId;

    /** 菜单ID */
    private Long menuId;

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
