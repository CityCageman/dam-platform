package com.dam.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统菜单实体（dam_sys.sys_menu）
 * <p>
 * 支撑平台侧边栏导航的数据库驱动：parent_id 为空的是顶级目录（大模块），
 * 子节点为具体页面菜单（menu_type=C）。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "sys_menu", schema = "dam_sys")
public class SysMenu {

    @TableId(type = IdType.AUTO)
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

    /** 图标（Element Plus 图标名或图片路径） */
    private String icon;

    /** 排序号 */
    private Integer sortOrder;

    /** 是否可见：0-隐藏，1-显示 */
    private Integer visible;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
}
