package com.dam.auth.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.auth.dto.LoginRequest;
import com.dam.auth.model.pojo.SysPermission;
import com.dam.auth.model.pojo.SysRole;
import com.dam.auth.model.pojo.SysRolePermission;
import com.dam.auth.model.pojo.SysUser;
import com.dam.auth.model.pojo.SysUserRole;
import com.dam.auth.repository.SysPermissionMapper;
import com.dam.auth.repository.SysRoleMapper;
import com.dam.auth.repository.SysRolePermissionMapper;
import com.dam.auth.repository.SysUserMapper;
import com.dam.auth.repository.SysUserRoleMapper;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 认证服务
 * 负责用户登录、登出、当前用户信息查询
 * 登录成功后基于 Sa-Token 签发 token，并更新用户最后登录信息（sys_user.last_login_at/last_login_ip）
 * <p>
 * login 与 /me 返回相同的角色上下文：roles（启用且未删除的角色编码）、dataScope（多角色取最强）、
 * defaultCockpit（按角色优先级取默认驾驶舱）和 permissions（super_admin 为 "*"，其他角色取权限点编码）。
 * 密码等敏感字段不进入响应。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 数据范围：全部 */
    private static final String DATA_SCOPE_ALL = "ALL";
    /** 数据范围：本部门 */
    private static final String DATA_SCOPE_DEPARTMENT = "DEPARTMENT";
    /** 数据范围：仅本人 */
    private static final String DATA_SCOPE_SELF = "SELF";
    /** 数据范围由强到弱，用于多角色取最强 */
    private static final List<String> DATA_SCOPE_ORDER =
            List.of(DATA_SCOPE_ALL, DATA_SCOPE_DEPARTMENT, DATA_SCOPE_SELF);

    /** 默认驾驶舱角色优先级 */
    private static final List<String> COCKPIT_ROLE_PRIORITY = List.of(
            "super_admin", "executive", "department_head", "data_ops",
            "developer", "governor", "business", "security");
    /** 角色编码到默认驾驶舱的兜底映射（数据库未配置时使用） */
    private static final Map<String, String> ROLE_COCKPIT = Map.of(
            "super_admin", "super",
            "executive", "executive",
            "department_head", "department",
            "data_ops", "ops",
            "developer", "developer",
            "governor", "governance",
            "business", "business",
            "security", "security");
    /** 允许的驾驶舱编码 */
    private static final Set<String> COCKPITS = Set.copyOf(ROLE_COCKPIT.values());
    /** 超级管理员角色编码 */
    private static final String SUPER_ADMIN = "super_admin";
    /** 超级管理员通配权限 */
    private static final String WILDCARD_PERMISSION = "*";

    /** 用户数据访问对象 */
    private final SysUserMapper userMapper;

    /** 角色数据访问对象 */
    private final SysRoleMapper roleMapper;

    /** 用户-角色关联数据访问对象 */
    private final SysUserRoleMapper userRoleMapper;

    /** 角色-权限关联数据访问对象 */
    private final SysRolePermissionMapper rolePermissionMapper;

    /** 权限点数据访问对象 */
    private final SysPermissionMapper permissionMapper;

    /** 密码加解密服务（登录密码 RSA 解密） */
    private final CryptoService cryptoService;

    /**
     * 用户登录
     * 校验用户名与密码（BCrypt），检查账号状态，签发 Sa-Token，并更新最后登录信息
     *
     * @param req 登录请求（用户名+密码）
     * @return 携带 token 与用户信息的成功结果
     */
    public Result<Map<String, Object>> login(LoginRequest req) {
        // 按用户名查询用户记录
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername())
        );
        // 用户不存在：统一提示（避免暴露用户名是否存在）
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        // 密码校验（BCrypt 哈希比对；前端传输为 RSA 密文，先解密再比对）
        if (!BCrypt.checkpw(cryptoService.decryptPassword(req.getPassword()), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        // 账号状态校验：0-禁用
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        // 更新最后登录信息（时间+IP，写入 sys_user 真实列）
        user.setLastLoginAt(java.time.LocalDateTime.now());
        user.setLastLoginIp(resolveClientIp());
        userMapper.updateById(user);

        // 签发 Sa-Token 会话
        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        // 返回 token 与用户信息
        return Result.success(buildLoginResult(tokenInfo.getTokenValue(), user));
    }

    /**
     * 解析客户端真实IP
     * 优先取 X-Forwarded-For 首个IP（网关转发场景），其次 X-Real-IP，最后回退本地地址
     *
     * @return 客户端IP字符串
     */
    private String resolveClientIp() {
        try {
            var req = cn.dev33.satoken.context.SaHolder.getRequest();
            // 优先从转发链首部提取真实IP
            String fwd = req.getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) {
                return fwd.split(",")[0].trim();
            }
            // 次选代理设置的直连IP
            String realIp = req.getHeader("X-Real-IP");
            return realIp != null && !realIp.isBlank() ? realIp : "127.0.0.1";
        } catch (Exception e) {
            // 解析失败时回退本地地址，不影响登录主流程
            return "127.0.0.1";
        }
    }

    /**
     * 组装登录结果（token + 用户信息）
     *
     * @param token Sa-Token 值
     * @param user  登录用户
     * @return 登录结果 Map
     */
    private Map<String, Object> buildLoginResult(String token, SysUser user) {
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userInfo", buildUserInfo(user));
        return result;
    }

    /**
     * 组装前端需要的用户信息（安全字段如密码不返回）
     * <p>
     * 角色上下文与 login、/me 保持一致；多个角色时数据范围取最强、默认驾驶舱按固定优先级选择。
     *
     * @param user 用户实体
     * @return 用户信息 Map
     */
    private Map<String, Object> buildUserInfo(SysUser user) {
        List<SysRole> roles = loadEnabledRoles(user.getId());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        // role/department/avatar 为过渡字段（exist=false），当前版本可能为空
        userInfo.put("role", user.getRole());
        userInfo.put("department", user.getDepartment());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("roles", extractRoleCodes(roles));
        userInfo.put("dataScope", resolveDataScope(roles));
        userInfo.put("defaultCockpit", resolveDefaultCockpit(roles));
        userInfo.put("permissions", resolvePermissions(roles));
        return userInfo;
    }

    /**
     * 查询用户启用且未删除的角色，并按默认驾驶舱优先级稳定排序
     *
     * @param userId 用户ID
     * @return 角色列表（可能为空）
     */
    private List<SysRole> loadEnabledRoles(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getStatus, 1)
                .isNull(SysUserRole::getDeletedAt)
                .orderByAsc(SysUserRole::getId));
        if (links.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> roleIds = new LinkedHashSet<>();
        for (SysUserRole link : links) {
            if (link.getRoleId() != null) {
                roleIds.add(link.getRoleId());
            }
        }
        if (roleIds.isEmpty()) {
            return List.of();
        }

        Map<Long, SysRole> roleMap = new HashMap<>();
        // @TableLogic 自动排除已逻辑删除的角色
        for (SysRole role : roleMapper.selectByIds(roleIds)) {
            roleMap.put(role.getId(), role);
        }

        List<SysRole> roles = new ArrayList<>();
        for (Long roleId : roleIds) {
            SysRole role = roleMap.get(roleId);
            if (role == null || !StringUtils.hasText(role.getRoleCode())) {
                continue;
            }
            if (!Integer.valueOf(1).equals(role.getStatus())) {
                continue;
            }
            roles.add(role);
        }
        // 稳定排序：固定优先级角色在前，其余保持用户-角色关联顺序
        roles.sort(Comparator.comparingInt(role -> cockpitRolePriority(role.getRoleCode())));
        return roles;
    }

    /**
     * 角色编码对应的默认驾驶舱优先级，未在固定清单中的角色排最后
     */
    private int cockpitRolePriority(String roleCode) {
        if (roleCode == null) {
            return Integer.MAX_VALUE;
        }
        for (int i = 0; i < COCKPIT_ROLE_PRIORITY.size(); i++) {
            if (COCKPIT_ROLE_PRIORITY.get(i).equalsIgnoreCase(roleCode)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 提取角色编码列表（大小写保持数据库原值，去重保序）
     */
    private List<String> extractRoleCodes(List<SysRole> roles) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (SysRole role : roles) {
            if (StringUtils.hasText(role.getRoleCode())) {
                codes.add(role.getRoleCode());
            }
        }
        return new ArrayList<>(codes);
    }

    /**
     * 多角色取最强数据范围：ALL > DEPARTMENT > SELF；无角色时回退 SELF
     */
    private String resolveDataScope(List<SysRole> roles) {
        String strongest = DATA_SCOPE_SELF;
        int strongestRank = DATA_SCOPE_ORDER.indexOf(DATA_SCOPE_SELF);
        for (SysRole role : roles) {
            String scope = normalizeDataScope(role.getDataScope());
            int rank = DATA_SCOPE_ORDER.indexOf(scope);
            if (rank >= 0 && rank < strongestRank) {
                strongest = scope;
                strongestRank = rank;
                if (rank == 0) {
                    break;
                }
            }
        }
        return strongest;
    }

    /**
     * 规整数据库中的数据范围；空值或非法值按 SELF 处理
     */
    private String normalizeDataScope(String value) {
        if (!StringUtils.hasText(value)) {
            return DATA_SCOPE_SELF;
        }
        String scope = value.trim().toUpperCase(Locale.ROOT);
        return DATA_SCOPE_ORDER.contains(scope) ? scope : DATA_SCOPE_SELF;
    }

    /**
     * 按固定角色优先级选择默认驾驶舱；无固定角色时回退首个角色的配置，最后回退 business
     */
    private String resolveDefaultCockpit(List<SysRole> roles) {
        for (String roleCode : COCKPIT_ROLE_PRIORITY) {
            for (SysRole role : roles) {
                if (roleCode.equalsIgnoreCase(role.getRoleCode())) {
                    String cockpit = normalizeCockpit(role.getDefaultCockpit());
                    if (cockpit != null) {
                        return cockpit;
                    }
                    String mapped = ROLE_COCKPIT.get(roleCode);
                    if (mapped != null) {
                        return mapped;
                    }
                }
            }
        }
        for (SysRole role : roles) {
            String cockpit = normalizeCockpit(role.getDefaultCockpit());
            if (cockpit != null) {
                return cockpit;
            }
        }
        return ROLE_COCKPIT.get("business");
    }

    /**
     * 规整数据库中的默认驾驶舱；空值或非法值返回 null，交由调用方兜底
     */
    private String normalizeCockpit(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cockpit = value.trim().toLowerCase(Locale.ROOT);
        return COCKPITS.contains(cockpit) ? cockpit : null;
    }

    /**
     * 解析角色权限点：super_admin 返回通配 "*"，其他角色读取 sys_role_permission
     * <p>
     * 仅统计启用且未删除的角色-权限关联；权限表无数据时返回空数组。
     */
    private List<String> resolvePermissions(List<SysRole> roles) {
        for (SysRole role : roles) {
            if (SUPER_ADMIN.equalsIgnoreCase(role.getRoleCode())) {
                return List.of(WILDCARD_PERMISSION);
            }
        }
        if (roles.isEmpty()) {
            return List.of();
        }

        List<Long> roleIds = roles.stream()
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }

        List<SysRolePermission> links = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>()
                        .in(SysRolePermission::getRoleId, roleIds)
                        .eq(SysRolePermission::getStatus, 1)
                        .isNull(SysRolePermission::getDeletedAt)
                        .orderByAsc(SysRolePermission::getId));

        LinkedHashSet<Long> permissionIds = new LinkedHashSet<>();
        for (SysRolePermission link : links) {
            if (link.getPermissionId() != null) {
                permissionIds.add(link.getPermissionId());
            }
        }
        if (permissionIds.isEmpty()) {
            return List.of();
        }

        Map<Long, SysPermission> permissionMap = new HashMap<>();
        for (SysPermission permission : permissionMapper.selectByIds(permissionIds)) {
            permissionMap.put(permission.getId(), permission);
        }

        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (Long permissionId : permissionIds) {
            SysPermission permission = permissionMap.get(permissionId);
            if (permission == null || !StringUtils.hasText(permission.getPermissionCode())) {
                continue;
            }
            if (permission.getStatus() != null && permission.getStatus() != 1) {
                continue;
            }
            codes.add(permission.getPermissionCode());
        }
        return new ArrayList<>(codes);
    }

    /**
     * 退出登录
     * 使当前会话失效（Sa-Token）
     */
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户信息
     */
    public Result<Map<String, Object>> getCurrentUser() {
        // 从会话中取当前用户ID
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Map<String, Object> userInfo = buildUserInfo(user);
        return Result.success(userInfo);
    }
}
