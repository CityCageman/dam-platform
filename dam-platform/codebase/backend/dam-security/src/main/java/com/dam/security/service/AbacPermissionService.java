package com.dam.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.common.exception.BusinessException;
import com.dam.security.entity.DamDataPermission;
import com.dam.security.repository.DamDataPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * ABAC数据权限服务
 * 基于属性（角色、部门、密级）的数据访问控制
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbacPermissionService {

    private final DamDataPermissionMapper permissionMapper;
    private final DamAssetMapper assetMapper;

    // ==================== 权限评估 ====================

    /**
     * 检查用户是否有权限执行指定操作
     */
    public boolean checkPermission(String role, String department, Long assetId, String action) {
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null) return false;

        return checkPermission(role, department, asset.getSecurityLevel(), action);
    }

    /**
     * 检查用户是否有权限访问指定密级的数据
     * 新表 security_data_permission 语义：subject_id=角色、action_type=操作、description=效果(allow/deny)
     */
    public boolean checkPermission(String role, String department, String securityLevel, String action) {
        // 管理员拥有全部权限
        if ("admin".equals(role)) return true;

        // 获取所有启用的策略
        List<DamDataPermission> policies = permissionMapper.selectList(
                new LambdaQueryWrapper<DamDataPermission>()
                        .eq(DamDataPermission::getStatus, 1));

        // 按优先级排序（高优先级优先）
        policies.sort(Comparator.comparingInt(DamDataPermission::getPriority).reversed());

        // 匹配策略
        for (DamDataPermission policy : policies) {
            String policyAction = policy.getActionType();
            if (!action.equals(policyAction) && !"*".equals(policyAction)) continue;

            // 主体（角色）匹配
            String subjectId = policy.getSubjectId();
            boolean roleMatch = subjectId == null || subjectId.isEmpty()
                    || "*".equals(subjectId) || subjectId.equals(role);
            if (!roleMatch) continue;

            // 密级匹配（资源ID作为密级标识时的兼容，主体类型 SUBJECT 时用 filter_condition 含密级关键字）
            if (securityLevel == null) securityLevel = "internal";
            String filter = policy.getFilterCondition();
            boolean levelMatch = filter == null || filter.isEmpty()
                    || "*".equals(filter) || filter.contains(securityLevel);
            if (!levelMatch) continue;

            // 返回策略效果（description 承载 allow/deny）
            return !"deny".equalsIgnoreCase(policy.getDescription());
        }

        // 默认拒绝
        return false;
    }

    /**
     * 获取用户可访问的资产密级列表（用于数据过滤）
     */
    public List<String> getAllowedLevels(String role, String department) {
        List<DamDataPermission> policies = permissionMapper.selectList(
                new LambdaQueryWrapper<DamDataPermission>()
                        .eq(DamDataPermission::getStatus, 1));

        return policies.stream()
                .filter(p -> {
                    boolean roleMatch = p.getSubjectId() == null || "*".equals(p.getSubjectId()) || p.getSubjectId().equals(role);
                    boolean allow = !"deny".equalsIgnoreCase(p.getDescription());
                    return roleMatch && allow;
                })
                .map(DamDataPermission::getFilterCondition)
                .filter(level -> level != null && !level.isEmpty() && !"*".equals(level))
                .distinct()
                .toList();
    }

    // ==================== 策略管理 ====================

    /**
     * 查询全部 ABAC 数据权限策略
     *
     * @return 策略列表
     */
    public List<DamDataPermission> getPolicies() {
        return permissionMapper.selectList(null);
    }

    /**
     * 新增 ABAC 数据权限策略（自动补全编码、优先级、生效时间与审计字段）
     *
     * @param policy 策略实体
     * @return 创建后的策略
     */
    public DamDataPermission createPolicy(DamDataPermission policy) {
        // 补全审计字段
        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());
        // 默认状态与优先级
        if (policy.getStatus() == null) policy.setStatus(1);
        if (policy.getPriority() == null) policy.setPriority(0);
        // 自动生成权限编码
        if (policy.getPermissionCode() == null) {
            policy.setPermissionCode("ABAC-" + System.currentTimeMillis() % 1000000);
        }
        // 默认生效时间为当前
        if (policy.getEffectiveFrom() == null) policy.setEffectiveFrom(LocalDateTime.now());
        if (policy.getCreatedBy() == null) policy.setCreatedBy("system");
        if (policy.getUpdatedBy() == null) policy.setUpdatedBy("system");
        permissionMapper.insert(policy);
        return policy;
    }

    /**
     * 更新 ABAC 数据权限策略（按主键更新非空字段）
     */
    public DamDataPermission updatePolicy(DamDataPermission policy) {
        // 记录更新时间
        policy.setUpdatedAt(LocalDateTime.now());
        permissionMapper.updateById(policy);
        return policy;
    }

    /**
     * 删除 ABAC 数据权限策略（逻辑删除）
     */
    public void deletePolicy(Long id) {
        permissionMapper.deleteById(id);
    }

    // ==================== 初始化默认策略 ====================

    /**
     * 初始化默认 ABAC 权限策略（仅在策略表为空时执行，服务启动时调用）
     */
    public void initDefaultPolicies() {
        // 已有策略则不重复初始化
        long count = permissionMapper.selectCount(null);
        if (count > 0) return;

        log.info("初始化ABAC默认权限策略...");

        // 预置默认策略：管理员全权限、分析员/开发/访客分级查看、禁止导出与跨级访问
        createPolicy(createPolicyObj("管理员全部权限", "admin", null, "*", "*", "allow", 100));
        createPolicy(createPolicyObj("分析员查看低密数据", "analyst", null, "low", "view", "allow", 50));
        createPolicy(createPolicyObj("分析员查看中密数据", "analyst", null, "medium", "view", "allow", 50));
        createPolicy(createPolicyObj("开发人员查看低密数据", "developer", null, "low", "view", "allow", 40));
        createPolicy(createPolicyObj("访客仅查看公开数据", "visitor", null, "public", "view", "allow", 30));
        createPolicy(createPolicyObj("禁止访客导出数据", "visitor", null, "*", "export", "deny", 100));
        createPolicy(createPolicyObj("禁止跨部门访问高密数据", "*", null, "high", "view", "deny", 80));
        createPolicy(createPolicyObj("禁止访问关键数据", "*", null, "critical", "*", "deny", 70));
    }

    /**
     * 组装默认策略实体（新表语义：filter_condition 承载密级、description 承载 allow/deny）
     *
     * @param name     策略名称
     * @param role     主体角色
     * @param dept     部门（预留）
     * @param level    可访问密级（写入 filter_condition）
     * @param action   操作类型
     * @param effect   allow/deny（写入 description）
     * @param priority 优先级
     * @return 策略实体
     */
    private DamDataPermission createPolicyObj(String name, String role, String dept,
                                               String level, String action, String effect, int priority) {
        DamDataPermission p = new DamDataPermission();
        p.setPermissionCode("ABAC-" + (name.hashCode() & 0x7fffffff));
        p.setPermissionName(name);
        p.setSubjectType("ROLE");
        p.setSubjectId(role);
        p.setResourceType("TABLE");
        p.setResourceId(0L);
        p.setActionType(action);
        p.setFilterCondition(level);   // 承载密级标识
        p.setDescription(effect);      // 承载 allow/deny 效果
        p.setPriority(priority);
        p.setStatus(1);
        p.setEffectiveFrom(LocalDateTime.now());
        return p;
    }
}
