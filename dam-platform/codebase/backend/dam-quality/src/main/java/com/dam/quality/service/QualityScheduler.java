package com.dam.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.operations.entity.DamTicket;
import com.dam.operations.repository.DamTicketMapper;
import com.dam.quality.entity.DamQualityCheck;
import com.dam.quality.entity.DamQualityRule;
import com.dam.quality.repository.DamQualityCheckMapper;
import com.dam.quality.repository.DamQualityRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 质量定时扫描调度器
 * <p>
 * 每小时自动执行启用规则的质量检测；
 * 检测得分（pass_rate）低于告警阈值（按 severity 映射）时：
 * 1. 自动创建质量整改工单（ticket_order，类型 quality_issue）
 * 2. 发送告警通知（AlarmNotificationService）
 * 注意：得分字段使用新表真实列 pass_rate，旧字段 score 已废弃。
 *
 * @author DAM Platform
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QualityScheduler {

    /** 质量规则 Mapper */
    private final DamQualityRuleMapper ruleMapper;

    /** 质量检测结果 Mapper */
    private final DamQualityCheckMapper checkMapper;

    /** 工单 Mapper（跨模块复用 operations） */
    private final DamTicketMapper ticketMapper;

    /** 质量检测扫描服务 */
    private final QualityScanService qualityScanService;

    /** 告警通知服务 */
    private final AlarmNotificationService alarmNotificationService;

    /**
     * 每小时执行一次启用的规则检测
     * <p>
     * 遍历启用规则执行检测，得分低于阈值时自动建单并通知，
     * 单条规则异常不影响整体调度。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledScan() {
        log.info("开始定时质量扫描...");
        // 查询全部启用规则
        List<DamQualityRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<DamQualityRule>()
                        .eq(DamQualityRule::getStatus, 1));
        for (DamQualityRule rule : rules) {
            try {
                // 执行检测（结果已落库）
                DamQualityCheck check = qualityScanService.executeRule(rule.getId());
                log.info("规则[{}]检测完成: passRate={}", resolveRuleName(rule), check.getPassRate());

                // 触发告警检查（按 severity 映射阈值：critical-80 high-60 medium-40 low-20）
                double alarmThreshold = resolveAlarmThreshold(rule);
                if (check.getPassRate() != null
                        && check.getPassRate().doubleValue() < alarmThreshold) {
                    double score = check.getPassRate().doubleValue();
                    String detail = String.format("检测得分 %.2f 低于告警阈值 %.2f", score, alarmThreshold);
                    log.warn("告警触发: 规则[{}] {}", resolveRuleName(rule), detail);

                    // 1. 自动创建整改工单
                    createQualityTicket(rule, check, detail);

                    // 2. 发送通知
                    alarmNotificationService.sendAlarm(rule, score, detail);
                }
            } catch (Exception e) {
                // 单条规则失败不中断整体调度
                log.error("规则[{}]定时扫描失败: {}", resolveRuleName(rule), e.getMessage());
            }
        }
        log.info("定时质量扫描完成，共执行{}条规则", rules.size());
    }

    /**
     * 自动创建质量整改工单（类型 quality_issue，状态待处理）
     *
     * @param rule   触发告警的质量规则
     * @param check  检测结果
     * @param detail 告警详情
     */
    private void createQualityTicket(DamQualityRule rule, DamQualityCheck check, String detail) {
        try {
            // 组装工单并补全审计字段
            DamTicket ticket = new DamTicket();
            ticket.setTicketNo("Q-" + System.currentTimeMillis() % 1000000);
            ticket.setTicketType("quality_issue");
            ticket.setTitle(String.format("[自动派单] 质量规则[%s]告警", resolveRuleName(rule)));
            ticket.setDescription(String.format(
                    "规则: %s(%s)\n当前得分: %.2f\n告警阈值: %.2f\n检测结果: 总记录%d, 异常%d\n触发时间: %s",
                    resolveRuleName(rule), rule.getRuleCode(),
                    check.getPassRate() != null ? check.getPassRate().doubleValue() : 0,
                    resolveAlarmThreshold(rule),
                    check.getTotalCount() != null ? check.getTotalCount() : 0,
                    check.getFailCount() != null ? check.getFailCount() : 0,
                    LocalDateTime.now()
            ));
            ticket.setPriority(getPriorityByScore(check.getPassRate() != null ? check.getPassRate().doubleValue() : 100));
            ticket.setStatus(0);
            // 关联到规则的目标表（技术元数据表ID）
            ticket.setRelatedAssetId(rule.getTargetTableId());
            java.time.LocalDateTime now = LocalDateTime.now();
            ticket.setCreatedAt(now);
            ticket.setUpdatedAt(now);
            ticket.setCreatedBy("system");
            ticket.setUpdatedBy("system");
            ticketMapper.insert(ticket);
            log.info("自动创建质量整改工单成功: ticketNo={}, title={}", ticket.getTicketNo(), ticket.getTitle());
        } catch (Exception e) {
            // 建单失败仅记录日志，不影响调度主流程
            log.error("自动创建质量整改工单失败: {}", e.getMessage());
        }
    }

    /**
     * 规则名称解析（ruleName 为旧字段兼容，为空时回退使用 ruleCode）
     *
     * @param rule 规则实体
     * @return 展示用规则名
     */
    private String resolveRuleName(DamQualityRule rule) {
        return rule.getRuleName() != null && !rule.getRuleName().isEmpty()
                ? rule.getRuleName() : rule.getRuleCode();
    }

    /**
     * 告警阈值（新表无 alarmThreshold 列，按 severity 映射：critical-80/high-60/medium-40/low-20）
     *
     * @param rule 规则实体
     * @return 告警阈值（默认 60）
     */
    private double resolveAlarmThreshold(DamQualityRule rule) {
        String severity = rule.getSeverity();
        if ("critical".equalsIgnoreCase(severity)) return 80;
        if ("high".equalsIgnoreCase(severity)) return 60;
        if ("medium".equalsIgnoreCase(severity)) return 40;
        if ("low".equalsIgnoreCase(severity)) return 20;
        return 60; // 默认
    }

    /**
     * 按得分映射工单优先级（<60-high，<80-medium，其余-low）
     *
     * @param score 质量得分（通过率）
     * @return 优先级字符串
     */
    private String getPriorityByScore(double score) {
        if (score < 60) return "high";
        if (score < 80) return "medium";
        return "low";
    }
}
