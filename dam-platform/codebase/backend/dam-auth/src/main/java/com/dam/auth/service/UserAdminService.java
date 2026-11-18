package com.dam.auth.service;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.auth.dto.ResetPasswordRequest;
import com.dam.auth.dto.RoleIdsRequest;
import com.dam.auth.dto.RoleResponse;
import com.dam.auth.dto.UserCreateRequest;
import com.dam.auth.dto.UserResponse;
import com.dam.auth.dto.UserUpdateRequest;
import com.dam.auth.model.pojo.SysRole;
import com.dam.auth.model.pojo.SysUser;
import com.dam.auth.model.pojo.SysUserRole;
import com.dam.auth.repository.SysRoleMapper;
import com.dam.auth.repository.SysUserMapper;
import com.dam.auth.repository.SysUserRoleMapper;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 系统配置-用户管理服务
 * <p>
 * 提供用户分页/详情/创建/更新/逻辑删除/重置密码，以及用户-角色授权。
 * 安全约束：
 * <ul>
 *   <li>管理端接口，调用方必须已登录（控制器统一调用 {@link AuthSupport#requireLogin()}）；</li>
 *   <li>当前没有 RBAC 管理权限模型，后续需增加 ADMIN 权限校验；</li>
 *   <li>禁止删除当前登录用户、禁止修改当前登录用户自己的角色；</li>
 *   <li>密码仅以 BCrypt 密文落库，响应不返回密码，错误信息不包含密码/hash。</li>
 * </ul>
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    /** 默认分页大小 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 单页最大条数 */
    private static final int MAX_PAGE_SIZE = 100;
    /** 关键字最大长度，避免超长 like 查询 */
    private static final int MAX_KEYWORD_LENGTH = 100;
    /** 部门字段为 NOT NULL，创建时未填写则使用该默认值 */
    private static final String DEFAULT_DEPARTMENT = "未分配";
    /** 新建用户尚未登录时的 last_login_ip 占位值（历史 schema 为 NOT NULL） */
    private static final String NEVER_LOGIN_IP = "0.0.0.0";
    /** 密码最小长度 */
    private static final int MIN_PASSWORD_LENGTH = 6;
    /** BCrypt 仅处理前 72 字节密码 */
    private static final int MAX_PASSWORD_BYTES = 72;
    /** 前端 RSA（2048位）密文约344字符；超过该阈值才尝试 RSA 解密，短密码按明文处理 */
    private static final int RSA_CIPHERTEXT_MIN_LENGTH = 128;

    /** 用户数据访问对象 */
    private final SysUserMapper userMapper;
    /** 角色数据访问对象 */
    private final SysRoleMapper roleMapper;
    /** 用户-角色关联数据访问对象 */
    private final SysUserRoleMapper userRoleMapper;
    /** 登录态支撑组件 */
    private final AuthSupport authSupport;
    /** 登录密码 RSA 解密服务（兼容前端密文入参，明文入参原样返回） */
    private final CryptoService cryptoService;

    /**
     * 分页查询用户
     *
     * @param keyword  用户名/真实姓名/部门关键字，可为空
     * @param status   状态过滤：0-禁用，1-启用，可为空
     * @param page     页码，从1开始
     * @param pageSize 每页条数，最大100
     * @return 分页结果（含角色信息）
     */
    public PageResult<UserResponse> pageUsers(String keyword, Integer status, Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        validateStatus(status);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            if (kw.length() > MAX_KEYWORD_LENGTH) {
                kw = kw.substring(0, MAX_KEYWORD_LENGTH);
            }
            String finalKw = kw;
            wrapper.and(w -> w.like(SysUser::getUsername, finalKw)
                    .or().like(SysUser::getRealName, finalKw)
                    .or().like(SysUser::getDepartment, finalKw));
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt).orderByDesc(SysUser::getId);

        Page<SysUser> dbPage = userMapper.selectPage(new Page<>(current, size), wrapper);
        PageResult<UserResponse> result = new PageResult<>();
        result.setPage(dbPage.getCurrent());
        result.setPageSize(dbPage.getSize());
        result.setTotal(dbPage.getTotal());
        result.setTotalPages(dbPage.getPages());
        result.setRecords(toUserResponses(dbPage.getRecords()));
        return result;
    }

    /**
     * 查询用户详情
     *
     * @param id 用户ID
     * @return 用户详情（含角色）
     */
    public UserResponse getUser(Long id) {
        SysUser user = requireUser(id);
        return toUserResponses(List.of(user)).get(0);
    }

    /**
     * 创建用户并绑定角色
     * <p>
     * 用户名未删除记录内唯一；密码使用 BCrypt 加密；用户与角色关联在同一事务内写入。
     *
     * @param req 创建请求
     * @return 创建后的用户详情
     */
    @Transactional
    public UserResponse createUser(UserCreateRequest req) {
        SysUser operator = authSupport.currentUser();
        String username = req.getUsername().trim();

        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(409, "用户名已存在");
        }

        int status = req.getStatus() == null ? 1 : req.getStatus();
        validateStatus(status);
        List<Long> roleIds = normalizeIds(req.getRoleIds());
        List<SysRole> roles = loadRoles(roleIds);

        LocalDateTime now = LocalDateTime.now();
        String passwordHash = hashPassword(req.getPassword());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordHash);
        // password_hash / salt 为历史迁移遗留的 NOT NULL 列，按初始化脚本约定写入占位值
        user.setPasswordHash(passwordHash);
        user.setSalt("");
        user.setRealName(trimToNull(req.getRealName()));
        user.setEmail(trimToNull(req.getEmail()));
        user.setPhone(trimToNull(req.getPhone()));
        user.setAvatar(trimToNull(req.getAvatar()));
        user.setDepartment(StringUtils.hasText(req.getDepartment())
                ? req.getDepartment().trim() : DEFAULT_DEPARTMENT);
        user.setStatus(status);
        // last_login_at / last_login_ip 为历史 NOT NULL 列，新建用户写入未登录占位值
        user.setLastLoginAt(now);
        user.setLastLoginIp(NEVER_LOGIN_IP);
        user.setCreatedAt(now);
        user.setCreatedBy(operator.getUsername());
        user.setUpdatedAt(now);
        user.setUpdatedBy(operator.getUsername());

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发创建同名用户时兜底，避免把数据库错误细节返回/记录到日志
            throw new BusinessException(409, "用户名已存在");
        }

        saveUserRoles(user.getId(), roles, operator.getUsername(), now);
        return toUserResponses(List.of(user)).get(0);
    }

    /**
     * 更新用户资料/状态/密码
     * <p>
     * 不支持修改 username；password 为空表示不修改；角色通过独立授权接口维护。
     *
     * @param id  用户ID
     * @param req 更新请求
     * @return 更新后的用户详情
     */
    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest req) {
        requireUser(id);
        SysUser operator = authSupport.currentUser();
        LocalDateTime now = LocalDateTime.now();

        SysUser update = new SysUser();
        update.setId(id);
        if (req.getRealName() != null) {
            update.setRealName(req.getRealName().trim());
        }
        if (req.getDepartment() != null) {
            update.setDepartment(StringUtils.hasText(req.getDepartment())
                    ? req.getDepartment().trim() : DEFAULT_DEPARTMENT);
        }
        if (req.getEmail() != null) {
            update.setEmail(req.getEmail().trim());
        }
        if (req.getPhone() != null) {
            update.setPhone(req.getPhone().trim());
        }
        if (req.getAvatar() != null) {
            update.setAvatar(req.getAvatar().trim());
        }
        if (req.getStatus() != null) {
            validateStatus(req.getStatus());
            update.setStatus(req.getStatus());
        }
        if (StringUtils.hasText(req.getPassword())) {
            String passwordHash = hashPassword(req.getPassword());
            update.setPassword(passwordHash);
            update.setPasswordHash(passwordHash);
        }
        update.setUpdatedAt(now);
        update.setUpdatedBy(operator.getUsername());

        userMapper.updateById(update);
        return toUserResponses(List.of(requireUser(id))).get(0);
    }

    /**
     * 逻辑删除用户（同时清理用户-角色关联）
     * <p>
     * 禁止删除当前登录用户，避免把当前会话的操作者删除。
     *
     * @param id 用户ID
     */
    @Transactional
    public void deleteUser(Long id) {
        SysUser operator = authSupport.currentUser();
        if (Objects.equals(id, operator.getId())) {
            throw new BusinessException(403, "不能删除当前登录用户");
        }
        requireUser(id);

        // @TableLogic 自动将 deleted_at 置为 now()，用户历史数据保留
        userMapper.deleteById(id);
        // 关联表全量清理，避免残留已删除用户的角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    /**
     * 重置用户密码（BCrypt）
     * <p>
     * 不返回密码，不记录密码相关日志。
     *
     * @param id  用户ID
     * @param req 重置密码请求
     */
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest req) {
        requireUser(id);
        SysUser operator = authSupport.currentUser();
        // 兼容 newPassword（任务约定）与 password（前端现有入参）两种字段
        String passwordHash = hashPassword(req.effectivePassword());

        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordHash);
        update.setPasswordHash(passwordHash);
        update.setUpdatedAt(LocalDateTime.now());
        update.setUpdatedBy(operator.getUsername());
        userMapper.updateById(update);
    }

    /**
     * 查询用户已授权角色
     *
     * @param id 用户ID
     * @return 角色列表
     */
    public List<RoleResponse> getUserRoles(Long id) {
        requireUser(id);
        List<SysUserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, id)
                        .isNull(SysUserRole::getDeletedAt)
                        .orderByAsc(SysUserRole::getId));
        return toRoleResponses(links);
    }

    /**
     * 全量替换用户角色
     * <p>
     * 管理端接口；当前没有 RBAC 管理权限模型，后续应增加 ADMIN 权限校验。
     * 至少禁止普通用户修改自己的角色：即使未来放开 ADMIN，管理员也只能修改他人角色。
     * 先物理删除旧关联再批量插入，整个过程在同一事务内保证原子性。
     *
     * @param id  用户ID
     * @param req 角色ID列表（空数组表示清空）
     * @return 替换后的角色列表
     */
    @Transactional
    public List<RoleResponse> replaceUserRoles(Long id, RoleIdsRequest req) {
        SysUser operator = authSupport.currentUser();
        if (Objects.equals(id, operator.getId())) {
            throw new BusinessException(403, "不能修改自己的角色");
        }
        requireUser(id);

        if (req.getRoleIds() == null) {
            throw new BusinessException(400, "roleIds 不能为空");
        }
        List<Long> roleIds = normalizeIds(req.getRoleIds());
        List<SysRole> roles = loadRoles(roleIds);

        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        saveUserRoles(id, roles, operator.getUsername(), LocalDateTime.now());
        return getUserRoles(id);
    }

    /**
     * 批量组装用户响应（角色信息一次批量查询，避免 N+1）
     */
    private List<UserResponse> toUserResponses(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = users.stream().map(SysUser::getId).filter(Objects::nonNull).toList();
        if (userIds.isEmpty()) {
            return List.of();
        }

        List<SysUserRole> links = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .in(SysUserRole::getUserId, userIds)
                        .isNull(SysUserRole::getDeletedAt)
                        .orderByAsc(SysUserRole::getId));

        LinkedHashSet<Long> roleIds = new LinkedHashSet<>();
        for (SysUserRole link : links) {
            if (link.getRoleId() != null) {
                roleIds.add(link.getRoleId());
            }
        }

        Map<Long, RoleResponse> roleMap = new HashMap<>();
        if (!roleIds.isEmpty()) {
            for (SysRole role : roleMapper.selectByIds(roleIds)) {
                roleMap.put(role.getId(), toRoleResponse(role));
            }
        }

        Map<Long, List<Long>> userRoleIds = new HashMap<>();
        Map<Long, List<RoleResponse>> userRoles = new HashMap<>();
        for (SysUserRole link : links) {
            RoleResponse role = roleMap.get(link.getRoleId());
            if (role == null) {
                // 角色已逻辑删除时不再返回，避免前端展示无效授权
                continue;
            }
            userRoleIds.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(role.getId());
            userRoles.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(role);
        }

        List<UserResponse> result = new ArrayList<>(users.size());
        for (SysUser user : users) {
            UserResponse vo = toUserResponse(user);
            vo.setRoleIds(userRoleIds.getOrDefault(user.getId(), List.of()));
            vo.setRoles(userRoles.getOrDefault(user.getId(), List.of()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 用户实体转响应对象（不包含任何密码字段）
     */
    private UserResponse toUserResponse(SysUser user) {
        UserResponse vo = new UserResponse();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setDepartment(user.getDepartment());
        vo.setStatus(user.getStatus());
        vo.setAvatar(user.getAvatar());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setLastLoginIp(user.getLastLoginIp());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setCreatedBy(user.getCreatedBy());
        vo.setUpdatedAt(user.getUpdatedAt());
        vo.setUpdatedBy(user.getUpdatedBy());
        return vo;
    }

    /**
     * 角色实体转响应对象
     */
    private RoleResponse toRoleResponse(SysRole role) {
        RoleResponse vo = new RoleResponse();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setCreatedBy(role.getCreatedBy());
        vo.setUpdatedAt(role.getUpdatedAt());
        vo.setUpdatedBy(role.getUpdatedBy());
        return vo;
    }

    /**
     * 关联记录转角色响应（仅返回未删除角色）
     */
    private List<RoleResponse> toRoleResponses(List<SysUserRole> links) {
        if (links == null || links.isEmpty()) {
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
        for (SysRole role : roleMapper.selectByIds(roleIds)) {
            roleMap.put(role.getId(), role);
        }
        List<RoleResponse> result = new ArrayList<>();
        for (Long roleId : roleIds) {
            SysRole role = roleMap.get(roleId);
            if (role != null) {
                result.add(toRoleResponse(role));
            }
        }
        return result;
    }

    /**
     * 批量写入用户-角色关联，按入参顺序将首个角色标记为默认角色
     */
    private void saveUserRoles(Long userId, List<SysRole> roles, String operator, LocalDateTime now) {
        for (int i = 0; i < roles.size(); i++) {
            SysUserRole link = new SysUserRole();
            link.setUserId(userId);
            link.setRoleId(roles.get(i).getId());
            link.setDefaultRole(i == 0 ? "Y" : "N");
            link.setStatus(1);
            link.setCreatedAt(now);
            link.setCreatedBy(operator);
            link.setUpdatedAt(now);
            link.setUpdatedBy(operator);
            userRoleMapper.insert(link);
        }
    }

    /**
     * 查询并校验角色ID列表（拒绝不存在或已删除的角色）
     */
    private List<SysRole> loadRoles(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectByIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(400, "存在无效或已删除的角色ID");
        }
        Map<Long, SysRole> roleMap = new HashMap<>();
        for (SysRole role : roles) {
            roleMap.put(role.getId(), role);
        }
        List<SysRole> ordered = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) {
            ordered.add(roleMap.get(roleId));
        }
        return ordered;
    }

    /**
     * 去重并校验ID列表（保留入参顺序）
     */
    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) {
                throw new BusinessException(400, "ID 列表不能包含空值");
            }
            distinct.add(id);
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 查询用户，不存在或已删除时返回 404
     */
    private SysUser requireUser(Long id) {
        if (id == null) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    /**
     * 校验状态取值：仅允许 0（禁用）/1（启用）
     */
    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(400, "状态只能为0或1");
        }
    }

    /**
     * 解密（兼容 RSA 密文/明文）并 BCrypt 加密密码，同时校验明文长度
     * <p>
     * 前端可能使用 /api/auth/public-key 对密码做 RSA 加密后再提交；
     * CryptoService.decryptPassword 在解密失败时原样返回入参，因此明文入参也可直接使用。
     * 错误信息不包含密码内容。
     */
    private String hashPassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BusinessException(400, "密码不能为空");
        }
        // 前端可能提交 RSA 密文（约344字符）；短内容按明文处理，避免无意义的解密告警
        String plainPassword = rawPassword.length() > RSA_CIPHERTEXT_MIN_LENGTH
                ? cryptoService.decryptPassword(rawPassword)
                : rawPassword;
        if (!StringUtils.hasText(plainPassword)) {
            throw new BusinessException(400, "密码不能为空");
        }
        if (plainPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(400, "密码长度不能少于6位");
        }
        if (plainPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessException(400, "密码过长");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    /**
     * 去除首尾空白，空字符串返回 null
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
