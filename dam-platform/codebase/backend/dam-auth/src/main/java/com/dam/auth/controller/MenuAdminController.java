package com.dam.auth.controller;

import com.dam.auth.dto.MenuResponse;
import com.dam.auth.service.AuthSupport;
import com.dam.auth.service.RoleAdminService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置-菜单选项接口（管理端）
 * <p>
 * 为前端“授权管理-角色授权”提供可选菜单列表（平铺结构，前端按 parentId 构建树）。
 * 路径 /api/auth/menus 与网关 /api/auth/** 路由兼容。
 * 要求登录并具备系统配置管理角色（ADMIN/governor）：未登录返回 401，非管理员返回 403。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/auth/menus")
@RequiredArgsConstructor
public class MenuAdminController {

    /** 角色/菜单管理服务 */
    private final RoleAdminService roleAdminService;
    /** 登录态支撑组件 */
    private final AuthSupport authSupport;

    /**
     * 查询全部未删除菜单（含禁用菜单，供授权树展示）
     *
     * @return 菜单平铺列表
     */
    @GetMapping
    public Result<List<MenuResponse>> listMenus() {
        authSupport.requireAdmin();
        return Result.success(roleAdminService.listAllMenus());
    }
}
