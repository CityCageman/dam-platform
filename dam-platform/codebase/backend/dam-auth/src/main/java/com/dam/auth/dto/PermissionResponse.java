package com.dam.auth.dto;

import lombok.Data;

/**
 * 角色权限点响应（可选能力，数据来自 sys_permission / sys_role_permission）
 *
 * @author DAM Platform
 */
@Data
public class PermissionResponse {

    /** 权限ID */
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
}
