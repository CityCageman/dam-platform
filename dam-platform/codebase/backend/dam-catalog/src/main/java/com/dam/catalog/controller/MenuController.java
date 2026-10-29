package com.dam.catalog.controller;

import com.dam.catalog.service.MenuService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统菜单接口（dam_sys.sys_menu）
 * <p>
 * 为前端侧边栏提供数据库驱动的菜单树，替代前端静态路由硬编码兜底。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/catalog/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /**
     * 获取菜单树（启用且可见）
     */
    @GetMapping
    public Result<List<Map<String, Object>>> getMenus() {
        return Result.success(menuService.getMenuTree());
    }
}
