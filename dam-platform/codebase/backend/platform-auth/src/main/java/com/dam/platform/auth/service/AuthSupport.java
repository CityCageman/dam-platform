package com.dam.platform.auth.service;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.platform.auth.model.pojo.SysRole;
import com.dam.platform.auth.model.pojo.SysUser;
import com.dam.platform.auth.model.pojo.SysUserRole;
import com.dam.platform.auth.repository.SysRoleMapper;
import com.dam.platform.auth.repository.SysUserMapper;
import com.dam.platform.auth.repository.SysUserRoleMapper;
import com.dam.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 管理端登录态与权限支撑组件
 * <p>
 * 所有 /api/auth/users/**、/api/auth/roles/**、/api/auth/menus 管理接口先调用
 * {@link #requireLogin()} 校验登录：网关会先校验 token，这里在服务内部再校验一次，
 * 防止绕过网关直连 dam-auth 时未登录访问管理接口（未登录统一返回业务码 401）。
 * <p>
 * 用户/角色/授权管理再调用 {@link #requireAdmin()} 校验管理角色：当前平台尚未建立
 * 完整的 RBAC 权限点模型，暂以初始化脚本中的 super_admin（超级管理员）、
 * ADMIN（系统管理员）或 governor（数据管理员）角色作为管理角色；
 * 后续引入权限点后可替换为权限码判断（例如 user:manage）。
 *
 * @author DAM Platform
 */
@Component
@RequiredArgsConstructor
public class AuthSupport {

    /** 允许访问系统配置的角色编码（兼容 super_admin、init-all 的 ADMIN 与初始化.sql 的 governor） */
    private static final Set<String> ADMIN_ROLE_CODES = Set.of("super_admin", "admin", "governor");

    /** 用户数据访问对象 */
    private final SysUserMapper userMapper;
    /** 用户-角色关联数据访问对象 */
    private final SysUserRoleMapper userRoleMapper;
    /** 角色数据访问对象 */
    private final SysRoleMapper roleMapper;

    /**
     * 校验当前请求已登录
     * <p>
     * Sa-Token 抛出的 NotLoginException 统一转换为业务码 401，
     * 避免被全局兜底异常处理成 500。
     */
    public void requireLogin() {
        try {
            StpUtil.checkLogin();
        } catch (NotLoginException e) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
    }

    /**
     * 校验当前登录用户具备系统配置管理权限
     * <p>
     * 系统配置包含用户、角色、菜单和授权管理，不能只依赖“已登录”。
     * 当前平台尚无统一 ADMIN 权限点，使用角色编码 super_admin（超级管理员）、
     * ADMIN（系统管理员）或 governor（数据管理员）作为管理角色。
     * 仅统计未删除且启用的用户-角色关联。
     */
    public void requireAdmin() {
        long userId = currentUserId();
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getStatus, 1)
                .isNull(SysUserRole::getDeletedAt));

        Set<Long> roleIds = new LinkedHashSet<>();
        for (SysUserRole link : links) {
            if (link.getRoleId() != null) {
                roleIds.add(link.getRoleId());
            }
        }

        boolean admin = false;
        if (!roleIds.isEmpty()) {
            for (SysRole role : roleMapper.selectByIds(roleIds)) {
                if (role.getStatus() == null || role.getStatus() != 1 || role.getRoleCode() == null) {
                    continue;
                }
                if (ADMIN_ROLE_CODES.contains(role.getRoleCode().toLowerCase(Locale.ROOT))) {
                    admin = true;
                    break;
                }
            }
        }
        if (!admin) {
            throw new BusinessException(403, "仅系统管理员可访问系统配置");
        }
    }

    /**
     * 获取当前登录用户ID（同时校验登录态）
     *
     * @return 当前登录用户ID
     */
    public long currentUserId() {
        requireLogin();
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前登录用户实体（同时校验登录态）
     * <p>
     * token 有效但用户已被删除时返回 401，避免继续以已删除用户身份操作。
     *
     * @return 当前登录用户
     */
    public SysUser currentUser() {
        long userId = currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录用户不存在或已被删除");
        }
        return user;
    }

    /**
     * 获取当前登录用户名（用于 created_by / updated_by 审计字段）
     *
     * @return 当前登录用户名
     */
    public String currentUsername() {
        return currentUser().getUsername();
    }
}
