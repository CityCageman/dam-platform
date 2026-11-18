package com.dam.auth.service;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.auth.model.pojo.SysRole;
import com.dam.auth.model.pojo.SysUser;
import com.dam.auth.model.pojo.SysUserRole;
import com.dam.auth.repository.SysRoleMapper;
import com.dam.auth.repository.SysUserMapper;
import com.dam.auth.repository.SysUserRoleMapper;
import com.dam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 管理端登录态支撑组件
 * <p>
 * 所有 /api/auth/users/** 与 /api/auth/roles/** 管理接口在进入业务逻辑前调用
 * {@link #requireLogin()}：网关会先校验 token，这里在服务内部再校验一次，
 * 防止绕过网关直连 dam-auth 时未登录访问管理接口（未登录统一返回业务码 401）。
 * <p>
 * 当前平台尚未建立 RBAC 管理权限模型，登录用户均可调用管理接口；后续应在此处
 * 增加 ADMIN 权限校验（例如 StpUtil.checkRole("ADMIN")），并由管理员操作他人账号。
 *
 * @author DAM Platform
 */
@Component
@RequiredArgsConstructor
public class AuthSupport {

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
     * 校验当前登录用户具备系统管理权限（数据管理员 governor）。
     * <p>
     * 系统配置包含用户、角色和授权管理，不能只依赖“已登录”。当前平台尚无
     * 统一 ADMIN 角色，使用初始化数据中的 governor（数据管理员）作为管理角色；
     * 后续引入 RBAC 权限点后可替换为权限码判断。
     */
    public void requireAdmin() {
        long userId = currentUserId();
        List<SysUserRole> links = userRoleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getStatus, 1));
        boolean admin = links.stream()
                .map(SysUserRole::getRoleId)
                .filter(java.util.Objects::nonNull)
                .map(roleMapper::selectById)
                .filter(java.util.Objects::nonNull)
                .map(SysRole::getRoleCode)
                .anyMatch(code -> "governor".equalsIgnoreCase(code));
        if (!admin) {
            throw new BusinessException(403, "仅数据管理员可访问系统配置");
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
