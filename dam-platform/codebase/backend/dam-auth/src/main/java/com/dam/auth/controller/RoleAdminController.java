package com.dam.auth.controller;

import com.dam.auth.dto.MenuIdsRequest;
import com.dam.auth.dto.MenuResponse;
import com.dam.auth.dto.PermissionResponse;
import com.dam.auth.dto.RoleCreateRequest;
import com.dam.auth.dto.RoleResponse;
import com.dam.auth.dto.RoleUpdateRequest;
import com.dam.auth.service.AuthSupport;
import com.dam.auth.service.RoleAdminService;
import com.dam.common.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置-角色管理与授权接口（管理端）
 * <p>
 * 路径统一在 /api/auth/roles 下，与网关 /api/auth/** 路由兼容。
 * 所有接口要求登录并具备系统配置管理角色：控制器入口统一调用
 * {@link AuthSupport#requireAdmin()}，未登录返回业务码 401，非管理员返回 403。
 * 当前以初始化角色 ADMIN/governor 作为管理角色，后续可替换为 RBAC 权限点。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/auth/roles")
@RequiredArgsConstructor
public class RoleAdminController {

    /** 角色管理服务 */
    private final RoleAdminService roleAdminService;
    /** 登录态支撑组件 */
    private final AuthSupport authSupport;

    /**
     * 查询角色
     * <p>
     * 不带 page/pageSize 时返回全量数组（任务约定的 GET /api/auth/roles）；
     * 前端系统配置页带分页参数时返回 PageResult，兼容两种消费方式。
     *
     * @param keyword  角色编码/名称关键字
     * @param status   状态：0-禁用，1-启用
     * @param page     页码，从1开始
     * @param pageSize 每页条数，最大200
     * @return 角色数组或分页结果
     */
    @GetMapping
    public Result<?> listRoles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        authSupport.requireAdmin();
        if (page == null && pageSize == null) {
            return Result.success(roleAdminService.listRoles(keyword, status));
        }
        return Result.success(roleAdminService.pageRoles(keyword, status, page, pageSize));
    }

    /**
     * 创建角色
     *
     * @param req 创建请求（roleCode/roleName 必填）
     * @return 创建后的角色
     */
    @PostMapping
    public Result<RoleResponse> createRole(@Valid @RequestBody RoleCreateRequest req) {
        authSupport.requireAdmin();
        return Result.success(roleAdminService.createRole(req));
    }

    /**
     * 更新角色
     *
     * @param id  角色ID
     * @param req 更新请求
     * @return 更新后的角色
     */
    @PutMapping("/{id}")
    public Result<RoleResponse> updateRole(@PathVariable Long id,
                                           @Valid @RequestBody RoleUpdateRequest req) {
        authSupport.requireAdmin();
        return Result.success(roleAdminService.updateRole(id, req));
    }

    /**
     * 逻辑删除角色（存在用户关联时返回 409）
     *
     * @param id 角色ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        authSupport.requireAdmin();
        roleAdminService.deleteRole(id);
        return Result.success();
    }

    /**
     * 查询角色已授权菜单
     *
     * @param id 角色ID
     * @return 菜单列表
     */
    @GetMapping("/{id}/menus")
    public Result<List<MenuResponse>> getRoleMenus(@PathVariable Long id) {
        authSupport.requireAdmin();
        return Result.success(roleAdminService.getRoleMenus(id));
    }

    /**
     * 全量替换角色菜单授权
     *
     * @param id  角色ID
     * @param req 菜单ID列表（空数组表示清空）
     * @return 替换后的菜单列表
     */
    @PutMapping("/{id}/menus")
    public Result<List<MenuResponse>> replaceRoleMenus(@PathVariable Long id,
                                                       @Valid @RequestBody MenuIdsRequest req) {
        authSupport.requireAdmin();
        return Result.success(roleAdminService.replaceRoleMenus(id, req));
    }

    /**
     * 查询角色权限点（可选能力，数据来自 sys_permission / sys_role_permission）
     *
     * @param id 角色ID
     * @return 权限点列表
     */
    @GetMapping("/{id}/permissions")
    public Result<List<PermissionResponse>> getRolePermissions(@PathVariable Long id) {
        authSupport.requireAdmin();
        return Result.success(roleAdminService.getRolePermissions(id));
    }
}
