package com.dam.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.common.exception.BusinessException;
import com.dam.quality.entity.DamQualityCheck;
import com.dam.quality.entity.DamQualityRule;
import com.dam.quality.repository.DamQualityCheckMapper;
import com.dam.quality.repository.DamQualityRuleMapper;
import com.dam.quality.repository.DynamicQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 质量检测扫描服务
 * <p>
 * 负责质量规则（metadata_quality_rule）的检测执行、结果记录（metadata_quality_check_result）
 * 与趋势/概览报表。
 * 检测 SQL 取自规则真实列 rule_expression（规则表达式/自定义 SQL），
 * 执行前经过白名单（仅 SELECT）与黑名单（危险关键字）双重安全校验，
 * 校验不通过或执行异常时标记失败并记录 error_detail，不影响其他规则执行。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QualityScanService {

    /** 质量规则 SQL 必须仅包含 SELECT 操作 */
    private static final Pattern SAFE_SQL_PATTERN = Pattern.compile(
            "^\\s*SELECT\\s+.*$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 禁止出现在规则 SQL 中的危险关键字（防注入） */
    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|EXEC|EXECUTE|CALL)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 质量规则 Mapper */
    private final DamQualityRuleMapper ruleMapper;

    /** 质量检测结果 Mapper */
    private final DamQualityCheckMapper checkMapper;

    /** 动态 SQL 查询 Mapper（执行规则表达式） */
    private final DynamicQueryMapper dynamicQueryMapper;

    /**
     * 执行单条质量规则的检测
     * <p>
     * 流程：创建待执行的检测记录 → 安全校验规则 SQL → 统计总数/异常数 →
     * 计算通过率与结果状态 → 回写检测记录；异常时记录 error_detail。
     *
     * @param ruleId 规则ID
     * @return 检测结果记录
     */
    public DamQualityCheck executeRule(Long ruleId) {
        // 查询规则并校验存在
        DamQualityRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) throw new BusinessException("规则不存在");

        // 预创建检测结果记录（状态 0-执行中）
        DamQualityCheck check = new DamQualityCheck();
        check.setRuleId(ruleId);
        check.setCheckTime(LocalDateTime.now());
        check.setStatus(0);
        java.time.LocalDateTime now = LocalDateTime.now();
        check.setCreatedAt(now);
        check.setUpdatedAt(now);
        check.setCreatedBy("system");
        check.setUpdatedBy("system");
        checkMapper.insert(check);

        try {
            int totalCount = 0;
            int errorCount = 0;

            // 规则表达式（新表真实列 rule_expression）非空时才执行 SQL 检测
            if (rule.getRuleExpression() != null && !rule.getRuleExpression().isBlank()) {
                // SQL 安全校验：白名单（仅 SELECT）+ 黑名单（危险关键字）
                String conditionSql = rule.getRuleExpression();
                if (!SAFE_SQL_PATTERN.matcher(conditionSql).matches()
                        || DANGEROUS_SQL_PATTERN.matcher(conditionSql).find()) {
                    log.warn("质量规则 [{}] 的 SQL 存在安全风险, 已跳过: {}", rule.getRuleCode(), conditionSql);
                    check.setStatus(2);
                    check.setUpdatedAt(LocalDateTime.now());
                    check.setUpdatedBy("system");
                    check.setErrorDetail("{\"error\":\"SQL存在安全风险，已跳过执行\"}");
                    checkMapper.updateById(check);
                    return check;
                }
                // 总记录数（将异常标记 __ERROR__ 替换为恒假条件统计全量）
                String totalSql = "SELECT COUNT(*) FROM (" + conditionSql.replace("__ERROR__", "1=0") + ") t";
                List<Map<String, Object>> totalResult = dynamicQueryMapper.executeQuery(totalSql);
                if (!totalResult.isEmpty()) {
                    Map<String, Object> row = totalResult.getFirst();
                    totalCount = row.values().stream().filter(Objects::nonNull).findFirst()
                            .map(v -> ((Number) v).intValue()).orElse(0);
                }

                // 异常记录数（直接执行规则表达式，返回不满足条件的行数）
                String errorSql = "SELECT COUNT(*) FROM (" + conditionSql + ") t";
                List<Map<String, Object>> errorResult = dynamicQueryMapper.executeQuery(errorSql);
                if (!errorResult.isEmpty()) {
                    Map<String, Object> row = errorResult.getFirst();
                    errorCount = row.values().stream().filter(Objects::nonNull).findFirst()
                            .map(v -> ((Number) v).intValue()).orElse(0);
                }
            }

            // 回写检测结果：总数/异常数/通过数/通过率/结果状态
            check.setTotalCount(totalCount);
            check.setFailCount(errorCount);
            check.setPassCount(Math.max(0, totalCount - errorCount));
            check.setPassRate(totalCount > 0 ? java.math.BigDecimal.valueOf((double) (totalCount - errorCount) / totalCount * 100).setScale(2, java.math.RoundingMode.HALF_UP) : java.math.BigDecimal.valueOf(100));
            check.setResultStatus(errorCount > 0 ? "FAIL" : "PASS");
            check.setStatus(1);
            check.setUpdatedAt(LocalDateTime.now());
            check.setUpdatedBy("system");
            check.setErrorDetail("{\"totalCount\":" + totalCount + ",\"errorCount\":" + errorCount + "}");
            checkMapper.updateById(check);

            // 检测不通过时记录告警日志（新表无告警阈值字段，按异常数>0 触发）
            if (errorCount > 0) {
                log.warn("规则[{}]检测未通过: 总记录数={}, 异常数={}, 通过率={}",
                        rule.getRuleCode(), totalCount, errorCount, check.getPassRate());
            }

        } catch (Exception e) {
            // 执行异常：标记失败并记录错误详情，不中断整体流程
            check.setStatus(2);
            check.setUpdatedAt(LocalDateTime.now());
            check.setUpdatedBy("system");
            check.setErrorDetail("{\"error\":\"" + e.getMessage() + "\"}");
            checkMapper.updateById(check);
            log.error("质量检测执行失败 [ruleId={}]: {}", ruleId, e.getMessage());
        }

        return check;
    }

    /**
     * 执行所有已启用的规则
     *
     * @return 全部规则检测结果列表
     */
    public List<DamQualityCheck> executeAllEnabled() {
        // 查询所有启用中的规则
        List<DamQualityRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<DamQualityRule>().eq(DamQualityRule::getStatus, 1));
        // 逐条执行检测
        return rules.stream().map(r -> executeRule(r.getId())).collect(Collectors.toList());
    }

    /**
     * 获取质量评分趋势（按规则过滤，取指定天数内的检测记录）
     *
     * @param ruleId 规则ID（可选，空则全部规则）
     * @param days   查询天数
     * @return 趋势数据 { dates, scores, avgScore }
     */
    public Map<String, Object> getQualityTrend(Long ruleId, int days) {
        // 查询起始时间
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<DamQualityCheck> checks = checkMapper.selectList(
                new LambdaQueryWrapper<DamQualityCheck>()
                        .eq(ruleId != null, DamQualityCheck::getRuleId, ruleId)
                        .ge(DamQualityCheck::getCheckTime, since)
                        .eq(DamQualityCheck::getStatus, 1)
                        .orderByAsc(DamQualityCheck::getCheckTime));

        // 组装日期与通过率序列
        List<String> dates = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (DamQualityCheck c : checks) {
            dates.add(c.getCheckTime().toLocalDate().toString());
            scores.add(c.getPassRate() != null ? c.getPassRate().doubleValue() : 0);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("scores", scores);
        // 平均通过率
        result.put("avgScore", scores.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        return result;
    }

    /**
     * 获取整体质量概览（规则总数/启用数/最近平均通过率/最近检测时间）
     *
     * @return 概览统计 Map
     */
    public Map<String, Object> getQualityOverview() {
        // 规则总数与启用数
        long totalRules = ruleMapper.selectCount(null);
        long enabledRules = ruleMapper.selectCount(
                new LambdaQueryWrapper<DamQualityRule>().eq(DamQualityRule::getStatus, 1));

        // 最近 100 次成功检测的平均通过率
        LambdaQueryWrapper<DamQualityCheck> lastWrapper = new LambdaQueryWrapper<>();
        lastWrapper.eq(DamQualityCheck::getStatus, 1)
                .orderByDesc(DamQualityCheck::getCheckTime)
                .last("LIMIT 100");
        List<DamQualityCheck> lastChecks = checkMapper.selectList(lastWrapper);
        double avgScore = lastChecks.stream()
                .filter(c -> c.getPassRate() != null)
                .mapToDouble(c -> c.getPassRate().doubleValue())
                .average().orElse(0);

        Map<String, Object> overview = new HashMap<>();
        overview.put("totalRules", totalRules);
        overview.put("enabledRules", enabledRules);
        overview.put("avgScore", Math.round(avgScore * 100) / 100.0);
        overview.put("lastCheckTime", lastChecks.isEmpty() ? null : lastChecks.getFirst().getCheckTime());
        return overview;
    }

    /**
     * 计算规则得分（保留工具方法，按通过率线性换算 0-100）
     *
     * @param rule       规则实体
     * @param errorCount 异常记录数
     * @param totalCount 总记录数
     * @return 得分（0-100）
     */
    private java.math.BigDecimal calculateScore(DamQualityRule rule, int errorCount, int totalCount) {
        if (totalCount == 0) return java.math.BigDecimal.valueOf(100);
        double errorRate = (double) errorCount / totalCount;
        double score = (1 - errorRate) * 100;
        return java.math.BigDecimal.valueOf(Math.round(score * 100) / 100.0);
    }
}
