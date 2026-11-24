package com.dam.platform.auth.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统权限点实体
 * <p>
 * 映射 dam_sys.sys_permission，仅用于角色权限查询（可选能力）；
 * 当前平台尚未基于权限点做接口鉴权，权限数据用于后续 RBAC 管理端。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "sys_permission", schema = "dam_sys")
public class SysPermission {

    /** 自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码 */
    private String permissionCode;

    /** 权限名称 */
    private String permissionName;

    /** 父权限ID */
    private Long parentId;

    /** 权限类型：menu/button/api */
    private String type;

    /** 路由路径 */
    private String path;

    /** 图标 */
    private String icon;

    /** 排序号 */
    private Integer sortOrder;

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

    /** 删除时间（逻辑删除） */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
