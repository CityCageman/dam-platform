package com.dam.platform.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.platform.auth.dto.MenuIdsRequest;
import com.dam.platform.auth.dto.MenuResponse;
import com.dam.platform.auth.dto.PermissionResponse;
import com.dam.platform.auth.dto.RoleCreateRequest;
import com.dam.platform.auth.dto.RoleResponse;
import com.dam.platform.auth.dto.RoleUpdateRequest;
import com.dam.platform.auth.model.pojo.SysMenu;
import com.dam.platform.auth.model.pojo.SysPermission;
import com.dam.platform.auth.model.pojo.SysRole;
import com.dam.platform.auth.model.pojo.SysRoleMenu;
import com.dam.platform.auth.model.pojo.SysRolePermission;
import com.dam.platform.auth.model.pojo.SysUser;
import com.dam.platform.auth.model.pojo.SysUserRole;
import com.dam.platform.auth.repository.SysMenuMapper;
import com.dam.platform.auth.repository.SysPermissionMapper;
import com.dam.platform.auth.repository.SysRoleMapper;
import com.dam.platform.auth.repository.SysRoleMenuMapper;
import com.dam.platform.auth.repository.SysRolePermissionMapper;
import com.dam.platform.auth.repository.SysUserRoleMapper;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 系统配置-角色管理服务
 * <p>
 * 提供角色列表/创建/更新/逻辑删除，以及角色-菜单、角色-权限查询。
 * 角色菜单授权采用全量替换语义，并在同一事务内先删后插保证原子性。
 * <p>
 * 管理接口由控制器调用 {@link AuthSupport#requireAdmin()} 校验登录态与系统配置管理角色
 * （当前接受 super_admin/admin/governor）；后续可替换为 RBAC 权限点。
 * <p>
 * 角色数据范围（data_scope）与默认驾驶舱（default_cockpit）在本服务统一做枚举校验，
 * 保证写入值可被登录上下文与前端驾驶舱路由直接使用。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    /** 默认分页大小 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 单页最大条数（前端角色授权下拉一次取200条） */
    private static final int MAX_PAGE_SIZE = 200;
    /** 关键字最大长度，避免超长 like 查询 */
    private static final int MAX_KEYWORD_LENGTH = 100;
    /** 数据范围默认值 */
    private static final String DEFAULT_DATA_SCOPE = "SELF";
    /** 默认驾驶舱默认值 */
    private static final String DEFAULT_COCKPIT = "business";
    /** 允许的数据范围 */
    private static final Set<String> DATA_SCOPES = Set.of("ALL", "DEPARTMENT", "SELF");
    /** 允许的默认驾驶舱 */
    private static final Set<String> COCKPITS = Set.of(
            "super", "executive", "department", "ops",
            "developer", "governance", "business", "security");

    /** 角色数据访问对象 */
    private final SysRoleMapper roleMapper;
    /** 菜单数据访问对象 */
    private final SysMenuMapper menuMapper;
    /** 角色-菜单关联数据访问对象 */
    private final SysRoleMenuMapper roleMenuMapper;
    /** 权限点数据访问对象（可选能力） */
    private final SysPermissionMapper permissionMapper;
    /** 角色-权限关联数据访问对象（可选能力） */
    private final SysRolePermissionMapper rolePermissionMapper;
    /** 用户-角色关联数据访问对象（删除角色前检查占用） */
    private final SysUserRoleMapper userRoleMapper;
    /** 登录态支撑组件 */
    private final AuthSupport authSupport;

    /**
     * 查询未删除角色（不分页，兼容 GET /api/auth/roles 直接返回数组的约定）
     *
     * @param keyword 角色编码/名称关键字，可为空
     * @param status  状态过滤：0-禁用，1-启用，可为空
     * @return 角色列表
     */
    public List<RoleResponse> listRoles(String keyword, Integer status) {
        List<SysRole> roles = roleMapper.selectList(roleQueryWrapper(keyword, status));
        List<RoleResponse> result = new ArrayList<>(roles.size());
        for (SysRole role : roles) {
            result.add(toRoleResponse(role));
        }
        return result;
    }

    /**
     * 分页查询角色（前端系统配置页使用 PageResult）
     *
     * @param keyword  角色编码/名称关键字，可为空
     * @param status   状态过滤：0-禁用，1-启用，可为空
     * @param page     页码，从1开始
     * @param pageSize 每页条数，最大200
     * @return 分页结果
     */
    public PageResult<RoleResponse> pageRoles(String keyword, Integer status, Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : Math.clamp(pageSize, 1, MAX_PAGE_SIZE);

        Page<SysRole> dbPage = roleMapper.selectPage(
                new Page<>(current, size), roleQueryWrapper(keyword, status));
        List<RoleResponse> records = new ArrayList<>(dbPage.getRecords().size());
        for (SysRole role : dbPage.getRecords()) {
            records.add(toRoleResponse(role));
        }
        PageResult<RoleResponse> result = new PageResult<>();
        result.setPage(dbPage.getCurrent());
        result.setPageSize(dbPage.getSize());
        result.setTotal(dbPage.getTotal());
        result.setTotalPages(dbPage.getPages());
        result.setRecords(records);
        return result;
    }

    /**
     * 查询全部未删除菜单（供前端角色授权树选择）
     * <p>
     * 返回平铺列表，前端按 parentId 自行构建树；包含禁用菜单，便于管理员调整历史授权。
     *
     * @return 菜单列表（按 sort_order、id 排序）
     */
    public List<MenuResponse> listAllMenus() {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
        List<MenuResponse> result = new ArrayList<>(menus.size());
        for (SysMenu menu : menus) {
            result.add(toMenuResponse(menu));
        }
        return result;
    }

    /**
     * 构造角色查询条件（关键字、状态、排序）
     */
    private LambdaQueryWrapper<SysRole> roleQueryWrapper(String keyword, Integer status) {
        validateStatus(status);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            if (kw.length() > MAX_KEYWORD_LENGTH) {
                kw = kw.substring(0, MAX_KEYWORD_LENGTH);
            }
            String finalKw = kw;
            wrapper.and(w -> w.like(SysRole::getRoleCode, finalKw)
                    .or().like(SysRole::getRoleName, finalKw));
        }
        if (status != null) {
            wrapper.eq(SysRole::getStatus, status);
        }
        wrapper.orderByAsc(SysRole::getId);
        return wrapper;
    }

    /**
     * 创建角色
     * <p>
     * roleCode 未删除记录内唯一，密码等敏感字段不涉及。
     *
     * @param req 创建请求
     * @return 创建后的角色
     */
    @Transactional
    public RoleResponse createRole(RoleCreateRequest req) {
        SysUser operator = authSupport.currentUser();
        String roleCode = req.getRoleCode().trim();

        Long exists = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
        if (exists != null && exists > 0) {
            throw new BusinessException(409, "角色编码已存在");
        }

        int status = req.getStatus() == null ? 1 : req.getStatus();
        validateStatus(status);
        LocalDateTime now = LocalDateTime.now();

        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(req.getRoleName().trim());
        role.setDescription(trimToNull(req.getDescription()));
        role.setStatus(status);
        role.setDataScope(resolveDataScope(req.getDataScope()));
        role.setDefaultCockpit(resolveDefaultCockpit(req.getDefaultCockpit()));
        role.setCreatedAt(now);
        role.setCreatedBy(operator.getUsername());
        role.setUpdatedAt(now);
        role.setUpdatedBy(operator.getUsername());

        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException e) {
            // 并发创建同编码角色时兜底，不返回数据库错误细节
            throw new BusinessException(409, "角色编码已存在");
        }
        return toRoleResponse(role);
    }

    /**
     * 更新角色
     * <p>
     * 各字段为空表示不修改；roleCode 允许修改但需保持唯一。
     *
     * @param id  角色ID
     * @param req 更新请求
     * @return 更新后的角色
     */
    @Transactional
    public RoleResponse updateRole(Long id, RoleUpdateRequest req) {
        SysRole existing = requireRole(id);
        SysUser operator = authSupport.currentUser();
        LocalDateTime now = LocalDateTime.now();

        SysRole update = new SysRole();
        update.setId(id);
        if (req.getRoleCode() != null) {
            String roleCode = req.getRoleCode().trim();
            if (!StringUtils.hasText(roleCode)) {
                throw new BusinessException(400, "角色编码不能为空");
            }
            if (!roleCode.equals(existing.getRoleCode())) {
                Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, roleCode)
                        .ne(SysRole::getId, id));
                if (exists != null && exists > 0) {
                    throw new BusinessException(409, "角色编码已存在");
                }
                update.setRoleCode(roleCode);
            }
        }
        if (req.getRoleName() != null) {
            String roleName = req.getRoleName().trim();
            if (!StringUtils.hasText(roleName)) {
                throw new BusinessException(400, "角色名称不能为空");
            }
            update.setRoleName(roleName);
        }
        if (req.getDescription() != null) {
            update.setDescription(req.getDescription().trim());
        }
        if (req.getStatus() != null) {
            validateStatus(req.getStatus());
            update.setStatus(req.getStatus());
        }
        if (req.getDataScope() != null) {
            update.setDataScope(validateDataScope(req.getDataScope()));
        }
        if (req.getDefaultCockpit() != null) {
            update.setDefaultCockpit(validateDefaultCockpit(req.getDefaultCockpit()));
        }
        update.setUpdatedAt(now);
        update.setUpdatedBy(operator.getUsername());

        try {
            roleMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "角色编码已存在");
        }
        return toRoleResponse(requireRole(id));
    }

    /**
     * 逻辑删除角色
     * <p>
     * 存在未删除的用户-角色关联时返回 409；删除角色同时清理角色-菜单关联。
     *
     * @param id 角色ID
     */
    @Transactional
    public void deleteRole(Long id) {
        requireRole(id);

        Long linkedUsers = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id)
                .isNull(SysUserRole::getDeletedAt));
        if (linkedUsers != null && linkedUsers > 0) {
            throw new BusinessException(409, "该角色已关联用户，无法删除");
        }

        // @TableLogic 自动将 deleted_at 置为 now()，角色历史数据保留
        roleMapper.deleteById(id);
        // 清理角色-菜单关联，避免残留无效授权
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
    }

    /**
     * 查询角色已授权菜单
     *
     * @param id 角色ID
     * @return 菜单列表（按 sort_order、id 排序）
     */
    public List<MenuResponse> getRoleMenus(Long id) {
        requireRole(id);
        List<SysRoleMenu> links = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, id)
                        .isNull(SysRoleMenu::getDeletedAt)
                        .orderByAsc(SysRoleMenu::getId));

        LinkedHashSet<Long> menuIds = new LinkedHashSet<>();
        for (SysRoleMenu link : links) {
            if (link.getMenuId() != null) {
                menuIds.add(link.getMenuId());
            }
        }
        if (menuIds.isEmpty()) {
            return List.of();
        }

        Map<Long, SysMenu> menuMap = new HashMap<>();
        for (SysMenu menu : menuMapper.selectByIds(menuIds)) {
            menuMap.put(menu.getId(), menu);
        }

        List<MenuResponse> result = new ArrayList<>();
        for (Long menuId : menuIds) {
            SysMenu menu = menuMap.get(menuId);
            if (menu != null) {
                result.add(toMenuResponse(menu));
            }
        }
        result.sort(Comparator.comparing(MenuResponse::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(MenuResponse::getId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }

    /**
     * 全量替换角色菜单授权
     * <p>
     * 管理端接口，控制器已校验 super_admin/admin/governor 管理角色；先物理删除旧关联再批量插入，
     * 整个过程在同一事务内保证原子性。
     *
     * @param id  角色ID
     * @param req 菜单ID列表（空数组表示清空）
     * @return 替换后的菜单列表
     */
    @Transactional
    public List<MenuResponse> replaceRoleMenus(Long id, MenuIdsRequest req) {
        requireRole(id);
        SysUser operator = authSupport.currentUser();

        if (req.getMenuIds() == null) {
            throw new BusinessException(400, "menuIds 不能为空");
        }
        List<Long> menuIds = normalizeIds(req.getMenuIds());
        if (!menuIds.isEmpty()) {
            List<SysMenu> menus = menuMapper.selectByIds(menuIds);
            if (menus.size() != menuIds.size()) {
                throw new BusinessException(400, "存在无效或已删除的菜单ID");
            }
        }

        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));

        LocalDateTime now = LocalDateTime.now();
        for (Long menuId : menuIds) {
            SysRoleMenu link = new SysRoleMenu();
            link.setRoleId(id);
            link.setMenuId(menuId);
            link.setStatus(1);
            link.setCreatedAt(now);
            link.setCreatedBy(operator.getUsername());
            link.setUpdatedAt(now);
            link.setUpdatedBy(operator.getUsername());
            roleMenuMapper.insert(link);
        }
        return getRoleMenus(id);
    }

    /**
     * 查询角色权限点（可选能力）
     * <p>
     * 数据来自 dam_sys.sys_permission / sys_role_permission；当前平台尚未基于权限点鉴权，
     * 该接口仅用于系统配置页展示。若后续部署省略权限表，本接口会返回系统错误，不影响其他接口。
     *
     * @param id 角色ID
     * @return 权限点列表（按 sort_order、id 排序）
     */
    public List<PermissionResponse> getRolePermissions(Long id) {
        requireRole(id);
        List<SysRolePermission> links = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, id)
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

        List<PermissionResponse> result = new ArrayList<>();
        for (Long permissionId : permissionIds) {
            SysPermission permission = permissionMap.get(permissionId);
            if (permission != null) {
                result.add(toPermissionResponse(permission));
            }
        }
        result.sort(Comparator.comparing(PermissionResponse::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(PermissionResponse::getId, Comparator.nullsLast(Long::compareTo)));
        return result;
    }

    /**
     * 查询角色，不存在或已删除时返回 404
     */
    private SysRole requireRole(Long id) {
        if (id == null) {
            throw new BusinessException(400, "角色ID不能为空");
        }
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        return role;
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
     * 校验状态取值：仅允许 0（禁用）/1（启用）
     */
    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(400, "状态只能为0或1");
        }
    }

    /**
     * 解析创建请求的数据范围：为空取默认值，否则校验枚举
     */
    private String resolveDataScope(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_DATA_SCOPE;
        }
        return validateDataScope(value);
    }

    /**
     * 校验数据范围枚举：仅允许 ALL/DEPARTMENT/SELF（忽略首尾空白与大小写）
     */
    private String validateDataScope(String value) {
        String scope = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!DATA_SCOPES.contains(scope)) {
            throw new BusinessException(400, "数据范围只能为 ALL/DEPARTMENT/SELF");
        }
        return scope;
    }

    /**
     * 解析创建请求的默认驾驶舱：为空取默认值，否则校验枚举
     */
    private String resolveDefaultCockpit(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_COCKPIT;
        }
        return validateDefaultCockpit(value);
    }

    /**
     * 校验默认驾驶舱枚举（忽略首尾空白与大小写）
     */
    private String validateDefaultCockpit(String value) {
        String cockpit = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!COCKPITS.contains(cockpit)) {
            throw new BusinessException(400,
                    "默认驾驶舱只能为 super/executive/department/ops/developer/governance/business/security");
        }
        return cockpit;
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
        vo.setDataScope(role.getDataScope());
        vo.setDefaultCockpit(role.getDefaultCockpit());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setCreatedBy(role.getCreatedBy());
        vo.setUpdatedAt(role.getUpdatedAt());
        vo.setUpdatedBy(role.getUpdatedBy());
        return vo;
    }

    /**
     * 菜单实体转响应对象
     */
    private MenuResponse toMenuResponse(SysMenu menu) {
        MenuResponse vo = new MenuResponse();
        vo.setId(menu.getId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setParentId(menu.getParentId());
        vo.setTreeLevel(menu.getTreeLevel());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setRedirect(menu.getRedirect());
        vo.setIcon(menu.getIcon());
        vo.setSortOrder(menu.getSortOrder());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        return vo;
    }

    /**
     * 权限点实体转响应对象
     */
    private PermissionResponse toPermissionResponse(SysPermission permission) {
        PermissionResponse vo = new PermissionResponse();
        vo.setId(permission.getId());
        vo.setPermissionCode(permission.getPermissionCode());
        vo.setPermissionName(permission.getPermissionName());
        vo.setParentId(permission.getParentId());
        vo.setType(permission.getType());
        vo.setPath(permission.getPath());
        vo.setIcon(permission.getIcon());
        vo.setSortOrder(permission.getSortOrder());
        vo.setStatus(permission.getStatus());
        return vo;
    }
}
