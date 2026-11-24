package com.dam.platform.auth.dto;

import lombok.Data;

/**
 * 角色菜单授权响应
 *
 * @author DAM Platform
 */
@Data
public class MenuResponse {

    /** 菜单ID */
    private Long id;

    /** 菜单名称 */
    private String menuName;

    /** 菜单类型：M-目录，C-菜单，F-按钮 */
    private String menuType;

    /** 父菜单ID（为空表示顶级） */
    private Long parentId;

    /** 树层级（1 为顶级） */
    private Integer treeLevel;

    /** 路由路径 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 重定向地址 */
    private String redirect;

    /** 图标 */
    private String icon;

    /** 排序号 */
    private Integer sortOrder;

    /** 是否可见：0-隐藏，1-显示 */
    private Integer visible;

    /** 状态：0-禁用，1-启用 */
    private Integer status;
}
