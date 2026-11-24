package com.dam.platform.auth.controller;

import com.dam.platform.auth.dto.ResetPasswordRequest;
import com.dam.platform.auth.dto.RoleIdsRequest;
import com.dam.platform.auth.dto.RoleResponse;
import com.dam.platform.auth.dto.UserCreateRequest;
import com.dam.platform.auth.dto.UserResponse;
import com.dam.platform.auth.dto.UserUpdateRequest;
import com.dam.platform.auth.service.AuthSupport;
import com.dam.platform.auth.service.UserAdminService;
import com.dam.platform.common.vo.PageResult;
import com.dam.platform.common.vo.Result;
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
 * 系统配置-用户管理接口（管理端）
 * <p>
 * 路径统一在 /api/auth/users 下，与网关 /api/auth/** 路由兼容。
 * 所有接口要求登录并具备系统配置管理角色：控制器入口统一调用
 * {@link AuthSupport#requireAdmin()}，未登录返回业务码 401，非管理员返回 403。
 * 当前以初始化角色 ADMIN/governor 作为管理角色，后续可替换为 RBAC 权限点。
 * <p>
 * 密码相关：请求中的明文密码仅用于服务端 BCrypt 加密，响应不返回密码，
 * 业务异常信息与日志均不包含密码/hash。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserAdminController {

    /** 用户管理服务 */
    private final UserAdminService userAdminService;
    /** 登录态支撑组件 */
    private final AuthSupport authSupport;

    /**
     * 分页查询用户
     *
     * @param keyword  用户名/真实姓名/部门关键字
     * @param status   状态：0-禁用，1-启用
     * @param page     页码，从1开始
     * @param pageSize 每页条数，最大200
     * @return 分页用户列表
     */
    @GetMapping
    public Result<PageResult<UserResponse>> pageUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        authSupport.requireAdmin();
        return Result.success(userAdminService.pageUsers(keyword, status, page, pageSize));
    }

    /**
     * 查询用户详情
     *
     * @param id 用户ID
     * @return 用户详情（含角色）
     */
    @GetMapping("/{id}")
    public Result<UserResponse> getUser(@PathVariable Long id) {
        authSupport.requireAdmin();
        return Result.success(userAdminService.getUser(id));
    }

    /**
     * 创建用户（可选同时绑定角色）
     *
     * @param req 创建请求（username/password 必填）
     * @return 创建后的用户详情
     */
    @PostMapping
    public Result<UserResponse> createUser(@Valid @RequestBody UserCreateRequest req) {
        authSupport.requireAdmin();
        return Result.success(userAdminService.createUser(req));
    }

    /**
     * 更新用户资料/状态/密码
     * <p>
     * 不允许修改 username；password 为空表示不修改。
     *
     * @param id  用户ID
     * @param req 更新请求
     * @return 更新后的用户详情
     */
    @PutMapping("/{id}")
    public Result<UserResponse> updateUser(@PathVariable Long id,
                                           @Valid @RequestBody UserUpdateRequest req) {
        authSupport.requireAdmin();
        return Result.success(userAdminService.updateUser(id, req));
    }

    /**
     * 逻辑删除用户（禁止删除当前登录用户）
     *
     * @param id 用户ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        authSupport.requireAdmin();
        userAdminService.deleteUser(id);
        return Result.success();
    }

    /**
     * 重置用户密码（BCrypt）
     *
     * @param id  用户ID
     * @param req 新密码
     * @return 空结果
     */
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequest req) {
        authSupport.requireAdmin();
        userAdminService.resetPassword(id, req);
        return Result.success();
    }

    /**
     * 查询用户已授权角色
     *
     * @param id 用户ID
     * @return 角色列表
     */
    @GetMapping("/{id}/roles")
    public Result<List<RoleResponse>> getUserRoles(@PathVariable Long id) {
        authSupport.requireAdmin();
        return Result.success(userAdminService.getUserRoles(id));
    }

    /**
     * 全量替换用户角色（禁止修改当前登录用户自己的角色）
     *
     * @param id  用户ID
     * @param req 角色ID列表（空数组表示清空）
     * @return 替换后的角色列表
     */
    @PutMapping("/{id}/roles")
    public Result<List<RoleResponse>> replaceUserRoles(@PathVariable Long id,
                                                       @Valid @RequestBody RoleIdsRequest req) {
        authSupport.requireAdmin();
        return Result.success(userAdminService.replaceUserRoles(id, req));
    }
}
