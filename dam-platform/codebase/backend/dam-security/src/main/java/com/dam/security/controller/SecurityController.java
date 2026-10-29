package com.dam.security.controller;

import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import com.dam.security.entity.DamAuditLog;
import com.dam.security.entity.DamDataAccessRequest;
import com.dam.security.entity.DamDataPermission;
import com.dam.security.entity.DamMaskingPolicy;
import com.dam.security.entity.DamSensitiveRule;
import com.dam.security.service.AbacPermissionService;
import com.dam.security.service.ClassificationService;
import com.dam.security.service.DataAccessRequestService;
import com.dam.security.service.MaskingUtil;
import com.dam.security.service.SecurityService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 安全管控接口
 * <p>
 * 提供：
 * <ul>
 *   <li>敏感数据规则（security_sensitive_rule）与脱敏策略（security_masking_policy）管理</li>
 *   <li>数据操作审计日志（security_data_audit_log）检索</li>
 *   <li>自动分类分级与敏感数据扫描</li>
 *   <li>ABAC 数据权限（security_data_permission）与数据访问申请（security_access_application）</li>
 *   <li>脱敏预览与可用算法列表</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {

    /** 安全规则/审计/脱敏策略服务 */
    private final SecurityService securityService;

    /** 自动分类分级服务 */
    private final ClassificationService classificationService;

    /** ABAC 数据权限服务 */
    private final AbacPermissionService abacPermissionService;

    /** 数据访问申请服务 */
    private final DataAccessRequestService dataAccessRequestService;

    /** 脱敏工具 */
    private final MaskingUtil maskingUtil;

    /**
     * 服务启动后初始化默认 ABAC 权限策略
     */
    @PostConstruct
    public void init() {
        abacPermissionService.initDefaultPolicies();
    }

    /**
     * 查询敏感数据规则列表
     */
    @GetMapping("/sensitive-rules")
    public Result<List<DamSensitiveRule>> getSensitiveRules() {
        return Result.success(securityService.getSensitiveRules());
    }

    /**
     * 新增敏感数据规则（自动补全审计字段）
     */
    @PostMapping("/sensitive-rules")
    public Result<DamSensitiveRule> createSensitiveRule(@RequestBody DamSensitiveRule rule) {
        return Result.success(securityService.createSensitiveRule(rule));
    }

    /**
     * 查询脱敏策略列表
     */
    @GetMapping("/masking-policies")
    public Result<List<DamMaskingPolicy>> getMaskingPolicies() {
        return Result.success(securityService.getMaskingPolicies());
    }

    /**
     * 新增脱敏策略（自动补全审计字段）
     */
    @PostMapping("/masking-policies")
    public Result<DamMaskingPolicy> createMaskingPolicy(@RequestBody DamMaskingPolicy policy) {
        return Result.success(securityService.createMaskingPolicy(policy));
    }

    /**
     * 分页检索数据操作审计日志（按操作/用户名过滤）
     */
    @GetMapping("/audit-logs")
    public Result<PageResult<DamAuditLog>> searchAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(securityService.searchAuditLogs(action, username, page, pageSize));
    }

    // ==================== 自动分类分级 ====================

    /**
     * 自动执行资产安全分类分级（全量）
     */
    @PostMapping("/classify")
    public Result<Map<String, Object>> autoClassify() {
        return Result.success(classificationService.autoClassify());
    }

    /**
     * 扫描指定资产/表的敏感数据（按敏感规则匹配采样）
     *
     * @param params 请求体：assetId-资产ID，tableName-表名，sampleSize-采样行数
     */
    @PostMapping("/scan-data")
    public Result<Map<String, Object>> scanSensitiveData(@RequestBody Map<String, Object> params) {
        // 解析请求参数（兼容不同数值类型）
        Long assetId = params.get("assetId") != null ? Long.valueOf(params.get("assetId").toString()) : null;
        String tableName = (String) params.get("tableName");
        int sampleSize = params.getOrDefault("sampleSize", 100) instanceof Number ?
                ((Number) params.get("sampleSize")).intValue() : 100;
        return Result.success(classificationService.scanSensitiveData(assetId, tableName, sampleSize));
    }

    // ==================== ABAC数据权限 ====================

    /**
     * 查询全部 ABAC 数据权限策略
     */
    @GetMapping("/permissions")
    public Result<List<DamDataPermission>> getPermissions() {
        return Result.success(abacPermissionService.getPolicies());
    }

    /**
     * 新增 ABAC 数据权限策略
     */
    @PostMapping("/permissions")
    public Result<DamDataPermission> createPermission(@RequestBody DamDataPermission permission) {
        return Result.success(abacPermissionService.createPolicy(permission));
    }

    /**
     * 更新 ABAC 数据权限策略
     */
    @PutMapping("/permissions")
    public Result<DamDataPermission> updatePermission(@RequestBody DamDataPermission permission) {
        return Result.success(abacPermissionService.updatePolicy(permission));
    }

    /**
     * 删除 ABAC 数据权限策略
     */
    @DeleteMapping("/permissions/{id}")
    public Result<Void> deletePermission(@PathVariable Long id) {
        abacPermissionService.deletePolicy(id);
        return Result.success();
    }

    /**
     * 校验指定角色/部门对资产的某操作是否有权限
     */
    @GetMapping("/check-permission")
    public Result<Boolean> checkPermission(
            @RequestParam String role,
            @RequestParam(required = false) String department,
            @RequestParam Long assetId,
            @RequestParam String action) {
        return Result.success(abacPermissionService.checkPermission(role, department, assetId, action));
    }

    // ==================== 数据使用申请 ====================

    /**
     * 分页检索数据访问申请（按用户/状态过滤）
     */
    @GetMapping("/access-requests")
    public Result<PageResult<DamDataAccessRequest>> searchAccessRequests(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(dataAccessRequestService.searchRequests(
                userId != null ? String.valueOf(userId) : null, status, page, pageSize));
    }

    /**
     * 创建数据访问申请（自动补全审计字段）
     */
    @PostMapping("/access-requests")
    public Result<DamDataAccessRequest> createAccessRequest(@RequestBody DamDataAccessRequest request) {
        return Result.success(dataAccessRequestService.createRequest(request));
    }

    /**
     * 审批数据访问申请
     *
     * @param id         申请ID
     * @param approved   是否批准
     * @param approverId 审批人ID
     * @param comment    审批意见
     */
    @PostMapping("/access-requests/{id}/approve")
    public Result<DamDataAccessRequest> approveRequest(
            @PathVariable Long id,
            @RequestParam boolean approved,
            @RequestParam Long approverId,
            @RequestParam(required = false) String comment) {
        return Result.success(dataAccessRequestService.approveRequest(id, approved, approverId, comment));
    }

    /**
     * 查询待审批的数据访问申请
     */
    @GetMapping("/pending-requests")
    public Result<List<DamDataAccessRequest>> getPendingRequests() {
        return Result.success(dataAccessRequestService.getPendingRequests());
    }

    // ==================== 脱敏预览 ====================

    /**
     * 脱敏预览：对指定值按算法进行脱敏
     */
    @PostMapping("/masking/preview")
    public Result<Map<String, Object>> previewMasking(@RequestBody Map<String, Object> params) {
        // 解析待脱敏值与算法（默认 mask）
        String value = (String) params.get("value");
        String algorithm = (String) params.getOrDefault("algorithm", "mask");
        String masked = maskingUtil.mask(value, algorithm);
        return Result.success(Map.of(
                "original", value,
                "masked", masked,
                "algorithm", algorithm
        ));
    }

    /**
     * 获取可用脱敏算法列表
     */
    @GetMapping("/masking/algorithms")
    public Result<List<String>> getAlgorithms() {
        return Result.success(List.copyOf(maskingUtil.getAvailableAlgorithms()));
    }
}
