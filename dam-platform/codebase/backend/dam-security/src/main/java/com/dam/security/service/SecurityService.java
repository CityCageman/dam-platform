package com.dam.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.vo.PageResult;
import com.dam.security.entity.DamAuditLog;
import com.dam.security.entity.DamMaskingPolicy;
import com.dam.security.entity.DamSensitiveRule;
import com.dam.security.repository.DamAuditLogMapper;
import com.dam.security.repository.DamMaskingPolicyMapper;
import com.dam.security.repository.DamSensitiveRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;
import java.util.List;

/**
 * 安全管控服务
 * <p>
 * 负责敏感数据规则（security_sensitive_rule）、脱敏策略（security_masking_policy）的
 * 查询与创建，以及数据操作审计日志（security_data_audit_log）的分页检索。
 * 注意：新表审计日志操作用户列为 operator_id、操作类型列为 operation_type。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class SecurityService {

    /** 敏感数据规则 Mapper */
    private final DamSensitiveRuleMapper sensitiveRuleMapper;

    /** 脱敏策略 Mapper */
    private final DamMaskingPolicyMapper maskingPolicyMapper;

    /** 数据操作审计日志 Mapper */
    private final DamAuditLogMapper auditLogMapper;

    // 敏感数据规则

    /**
     * 查询全部敏感数据规则（返回前填充前端兼容字段 sensitiveType/level/regexPattern/fieldNamePattern）
     *
     * @return 规则列表
     */
    public List<DamSensitiveRule> getSensitiveRules() {
        // 查询全部规则（无过滤条件）
        List<DamSensitiveRule> list = sensitiveRuleMapper.selectList(null);
        for (DamSensitiveRule r : list) {
            fillSensitiveRuleCompat(r);
        }
        return list;
    }

    /**
     * 新增敏感数据规则（自动补全审计字段，兼容前端旧字段名）
     * <p>
     * 兼容映射：regexPattern/fieldNamePattern → rulePattern、
     * sensitiveType → ruleType、level → sensitivityLevel。
     *
     * @param rule 规则实体
     * @return 创建后的规则（含自增ID）
     */
    @Transactional
    public DamSensitiveRule createSensitiveRule(DamSensitiveRule rule) {
        // 兼容前端字段：正则/字段匹配 → rulePattern
        if (StrUtil.isBlank(rule.getRulePattern())) {
            String pattern = StrUtil.isNotBlank(rule.getRegexPattern()) ? rule.getRegexPattern()
                    : rule.getFieldNamePattern();
            if (StrUtil.isNotBlank(pattern)) rule.setRulePattern(pattern);
        }
        // 兼容前端字段：sensitiveType → ruleType
        if (StrUtil.isBlank(rule.getRuleType()) && StrUtil.isNotBlank(rule.getSensitiveType())) {
            rule.setRuleType(rule.getSensitiveType());
        }
        // 兼容前端字段：level → sensitivityLevel
        if (StrUtil.isBlank(rule.getSensitivityLevel()) && StrUtil.isNotBlank(rule.getLevel())) {
            rule.setSensitivityLevel(rule.getLevel());
        }
        // 补全审计字段（表审计字段非空）
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        if (rule.getCreatedBy() == null) rule.setCreatedBy("system");
        if (rule.getUpdatedBy() == null) rule.setUpdatedBy("system");
        sensitiveRuleMapper.insert(rule);
        return rule;
    }

    /**
     * 填充敏感规则前端兼容字段（sensitiveType/level/regexPattern/fieldNamePattern）
     *
     * @param rule 规则实体（兼容字段不持久化）
     */
    private void fillSensitiveRuleCompat(DamSensitiveRule rule) {
        if (rule == null) return;
        // level ← sensitivityLevel 小写
        if (rule.getLevel() == null && rule.getSensitivityLevel() != null) {
            rule.setLevel(rule.getSensitivityLevel().toLowerCase());
        }
        // sensitiveType ← ruleType
        if (rule.getSensitiveType() == null) rule.setSensitiveType(rule.getRuleType());
        // regexPattern/fieldNamePattern ← rulePattern
        if (rule.getRegexPattern() == null) rule.setRegexPattern(rule.getRulePattern());
        if (rule.getFieldNamePattern() == null) rule.setFieldNamePattern(rule.getRulePattern());
    }

    // 脱敏策略

    /**
     * 查询全部脱敏策略
     *
     * @return 策略列表
     */
    public List<DamMaskingPolicy> getMaskingPolicies() {
        // 查询全部策略（无过滤条件）
        return maskingPolicyMapper.selectList(null);
    }

    /**
     * 新增脱敏策略（自动补全审计字段）
     *
     * @param policy 策略实体
     * @return 创建后的策略（含自增ID）
     */
    @Transactional
    public DamMaskingPolicy createMaskingPolicy(DamMaskingPolicy policy) {
        // 补全审计字段（表审计字段非空）
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        policy.setCreatedAt(now);
        policy.setUpdatedAt(now);
        if (policy.getCreatedBy() == null) policy.setCreatedBy("system");
        if (policy.getUpdatedBy() == null) policy.setUpdatedBy("system");
        maskingPolicyMapper.insert(policy);
        return policy;
    }

    /**
     * 分页查询审计日志
     * 适配新表 security_data_audit_log：操作类型列 operation_type、操作人列 operator_id
     *
     * @param action   操作类型（匹配 operation_type）
     * @param username 操作人标识（模糊匹配 operator_id）
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页审计日志列表
     */
    public PageResult<DamAuditLog> searchAuditLogs(String action, String username,
                                                    Integer page, Integer pageSize) {
        // 构造分页对象
        Page<DamAuditLog> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<DamAuditLog> wrapper = new LambdaQueryWrapper<>();
        // 按操作类型过滤（新表列 operation_type）
        if (StrUtil.isNotBlank(action)) wrapper.eq(DamAuditLog::getOperationType, action);
        // 按操作人ID模糊过滤（新表列 operator_id）
        if (StrUtil.isNotBlank(username)) wrapper.like(DamAuditLog::getOperatorId, username);
        // 按操作时间倒序排列（新表列 operation_time）
        wrapper.orderByDesc(DamAuditLog::getOperationTime);
        PageResult<DamAuditLog> result = PageResult.of(auditLogMapper.selectPage(p, wrapper));
        // 填充前端兼容字段（username/action/ipAddress）
        for (DamAuditLog log : result.getRecords()) {
            log.setUsername(log.getOperatorId());
            log.setAction(log.getOperationType());
            log.setIpAddress(log.getOperatorIp());
        }
        return result;
    }
}
