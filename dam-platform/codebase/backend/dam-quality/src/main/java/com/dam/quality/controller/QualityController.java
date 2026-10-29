package com.dam.quality.controller;

import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import com.dam.quality.entity.DamQualityCheck;
import com.dam.quality.entity.DamQualityRule;
import com.dam.quality.service.QualityScanService;
import com.dam.quality.service.QualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据质量接口
 * <p>
 * 提供：
 * <ul>
 *   <li>质量规则（metadata_quality_rule）的检索/增改删</li>
 *   <li>检测执行（metadata_quality_check_result）与检测历史</li>
 *   <li>质量趋势与概览报表</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    /** 质量规则服务 */
    private final QualityService qualityService;

    /** 质量检测扫描服务 */
    private final QualityScanService qualityScanService;

    /**
     * 分页检索质量规则（支持关键字/类型/状态过滤）
     */
    @GetMapping("/rules/search")
    public Result<PageResult<DamQualityRule>> searchRules(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(qualityService.searchRules(keyword, ruleType, status, page, pageSize));
    }

    /**
     * 创建质量规则（自动补全审计字段）
     */
    @PostMapping("/rules")
    public Result<DamQualityRule> createRule(@RequestBody DamQualityRule rule) {
        return Result.success(qualityService.createRule(rule));
    }

    /**
     * 更新质量规则
     */
    @PutMapping("/rules")
    public Result<DamQualityRule> updateRule(@RequestBody DamQualityRule rule) {
        return Result.success(qualityService.updateRule(rule));
    }

    /**
     * 删除质量规则（逻辑删除）
     */
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        qualityService.deleteRule(id);
        return Result.success();
    }

    /**
     * 查询规则的检测历史
     */
    @GetMapping("/checks/{ruleId}")
    public Result<List<DamQualityCheck>> getCheckHistory(@PathVariable Long ruleId) {
        return Result.success(qualityService.getCheckHistory(ruleId));
    }

    // ==================== 检测执行 ====================

    /**
     * 执行单个质量规则检测（生成检测结果记录）
     */
    @PostMapping("/rules/{ruleId}/execute")
    public Result<DamQualityCheck> executeRule(@PathVariable Long ruleId) {
        return Result.success(qualityScanService.executeRule(ruleId));
    }

    /**
     * 执行全部启用规则的检测
     */
    @PostMapping("/rules/execute-all")
    public Result<List<DamQualityCheck>> executeAll() {
        return Result.success(qualityScanService.executeAllEnabled());
    }

    // ==================== 报表 ====================

    /**
     * 获取质量趋势（按日/按规则的通过率趋势）
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(qualityScanService.getQualityTrend(ruleId, days));
    }

    /**
     * 获取质量概览（规则数/检测次数/平均通过率等）
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        return Result.success(qualityScanService.getQualityOverview());
    }
}
