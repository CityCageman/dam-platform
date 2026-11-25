package com.dam.platform.auth.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统菜单实体
 * <p>
 * 映射 dam_dpm.sys_menu，仅用于角色-菜单授权查询；菜单本身由系统配置模块维护。
 *
 * @author DAM Platform
 */
@Data
@TableName(value = "sys_menu", schema = "dam_dpm")
public class SysMenu {

    /** 自增ID */
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

    /** 图标 */
    private String icon;

    /** 排序号 */
    private Integer sortOrder;

    /** 是否可见：0-隐藏，1-显示 */
    private Integer visible;

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
