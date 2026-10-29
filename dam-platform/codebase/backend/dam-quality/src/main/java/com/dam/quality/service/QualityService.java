package com.dam.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.common.vo.PageResult;
import com.dam.quality.entity.DamQualityCheck;
import com.dam.quality.entity.DamQualityRule;
import com.dam.quality.repository.DamQualityCheckMapper;
import com.dam.quality.repository.DamQualityRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;
import java.util.List;

/**
 * 数据质量规则服务
 * <p>
 * 负责质量规则（metadata_quality_rule）的检索/增改删与检测历史（metadata_quality_check_result）查询。
 * 注意：新表无 rule_name 列，关键字检索基于 rule_code 真实列；
 * status 为 SMALLINT（1-启用，0-禁用），外部字符串需经 {@link #statusToCode(String)} 转换。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class QualityService {

    /** 质量规则 Mapper */
    private final DamQualityRuleMapper ruleMapper;

    /** 质量检测结果 Mapper */
    private final DamQualityCheckMapper checkMapper;

    /**
     * 分页检索质量规则（支持关键字/规则类型/状态过滤）
     *
     * @param keyword  关键字（模糊匹配 ruleCode）
     * @param ruleType 规则类型
     * @param status   状态字符串（enabled/active/disabled）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页规则结果
     */
    public PageResult<DamQualityRule> searchRules(String keyword, String ruleType, String status,
                                                   Integer page, Integer pageSize) {
        // 构造分页对象
        Page<DamQualityRule> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<DamQualityRule> wrapper = new LambdaQueryWrapper<>();
        // 关键字模糊匹配规则编码（新表无 rule_name 列，避免引用不存在字段）
        if (StrUtil.isNotBlank(keyword)) wrapper.like(DamQualityRule::getRuleCode, keyword);
        // 按规则类型过滤
        if (StrUtil.isNotBlank(ruleType)) wrapper.eq(DamQualityRule::getRuleType, ruleType);
        // 按状态过滤（字符串转整数编码）
        if (StrUtil.isNotBlank(status)) wrapper.eq(DamQualityRule::getStatus, statusToCode(status));
        // 按创建时间倒序
        wrapper.orderByDesc(DamQualityRule::getCreatedAt);
        PageResult<DamQualityRule> result = PageResult.of(ruleMapper.selectPage(p, wrapper));
        // 填充前端兼容字段 ruleName（新表无 rule_name 列，用 ruleCode 展示）
        for (DamQualityRule r : result.getRecords()) {
            r.setRuleName(r.getRuleCode());
        }
        return result;
    }

    /**
     * 创建质量规则（自动补全审计字段与默认状态）
     * <p>
     * 兼容前端字段：conditionSql → ruleExpression（新表检测 SQL 真实列），
     * ruleName/threshold 为新表不存在的旧字段（忽略，名称以 ruleCode 展示）。
     *
     * @param rule 规则实体
     * @return 创建后的规则（含自增ID）
     */
    @Transactional
    public DamQualityRule createRule(DamQualityRule rule) {
        // 兼容前端字段：conditionSql → ruleExpression
        if (StrUtil.isBlank(rule.getRuleExpression()) && StrUtil.isNotBlank(rule.getConditionSql())) {
            rule.setRuleExpression(rule.getConditionSql());
        }
        // 补全审计字段（非空约束）与默认状态
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        if (rule.getCreatedBy() == null) rule.setCreatedBy("system");
        if (rule.getUpdatedBy() == null) rule.setUpdatedBy("system");
        if (rule.getStatus() == null) rule.setStatus(1);
        ruleMapper.insert(rule);
        return rule;
    }

    /**
     * 更新质量规则（按主键更新非空字段）
     */
    @Transactional
    public DamQualityRule updateRule(DamQualityRule rule) { ruleMapper.updateById(rule); return rule; }

    /**
     * 删除质量规则（逻辑删除）
     */
    @Transactional
    public void deleteRule(Long id) { ruleMapper.deleteById(id); }

    /**
     * 查询规则的检测历史（按检测时间倒序）
     *
     * @param ruleId 规则ID
     * @return 检测结果列表
     */
    public List<DamQualityCheck> getCheckHistory(Long ruleId) {
        LambdaQueryWrapper<DamQualityCheck> wrapper = new LambdaQueryWrapper<>();
        // 按规则ID过滤
        wrapper.eq(DamQualityCheck::getRuleId, ruleId);
        // 按检测时间倒序
        wrapper.orderByDesc(DamQualityCheck::getCheckTime);
        return checkMapper.selectList(wrapper);
    }

    /**
     * 状态字符串 → 状态编码（1-启用 0-禁用）
     *
     * @param status 状态字符串（enabled/active 等）
     * @return 整数编码，未知状态默认 1
     */
    private Integer statusToCode(String status) {
        if (status == null) return 1;
        return "enabled".equalsIgnoreCase(status) || "active".equalsIgnoreCase(status) ? 1 : 0;
    }
}
