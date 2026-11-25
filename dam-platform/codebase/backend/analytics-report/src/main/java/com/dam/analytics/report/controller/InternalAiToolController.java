package com.dam.analytics.report.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.analytics.report.client.dto.MetricSpec;
import com.dam.analytics.report.entity.ReportCatalog;
import com.dam.analytics.report.service.ReportCatalogService;
import com.dam.analytics.report.service.ReportService;
import com.dam.platform.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 供 ai-workbench / analytics-metric 调用的报表领域工具接口。
 * <p>
 * 这些接口不直接面向浏览器，避免 AI 服务直接访问报表数据库。
 * 资产 AI 标注/分类能力已随 asset-catalog 依赖拆分迁回 catalog 服务，
 * 本控制器仅保留报表生成、指标 SQL 生成等报表领域能力。
 */
@RestController
@RequestMapping("/api/catalog/internal/ai")
@RequiredArgsConstructor
public class InternalAiToolController {

    private final ReportService reportService;
    private final ReportCatalogService reportCatalogService;

    private void requireLogin() {
        StpUtil.checkLogin();
    }

    /**
     * 供 analytics-metric 调用的指标 SQL 生成接口。
     * <p>
     * 指标主数据由 analytics-metric 独占，报表服务只负责复用元数据/知识库/模型网关能力生成 SQL，
     * 不落库、不读写 dam_metric 表。
     *
     * @param metric 指标定义（analytics-metric 传入的 DTO）
     * @param token  当前登录 token（缺省时从 Sa-Token 上下文读取）
     * @return {sql, dataSourceName, tablesUsed, params}
     */
    @PostMapping("/metrics/sql")
    public Result<Map<String, Object>> generateMetricSql(
            @RequestBody MetricSpec metric,
            @RequestHeader(value = "token", required = false) String token) {
        requireLogin();
        String effectiveToken = token == null || token.isBlank() ? StpUtil.getTokenValue() : token;
        return Result.success(reportService.generateMetricSql(metric, effectiveToken));
    }

    @PostMapping("/reports/generate")
    public Result<Map<String, Object>> generateReport(@RequestBody Map<String, Object> body) {
        requireLogin();
        return Result.success(reportService.generate(
                str(body.get("requirement")), longVal(body.get("dataSourceId")),
                str(body.get("token"))));
    }

    @PostMapping("/reports/title")
    public Result<String> generateTitle(@RequestBody Map<String, Object> body) {
        requireLogin();
        return Result.success(reportService.generateTitle(
                str(body.get("requirement")), str(body.get("token"))));
    }

    @GetMapping("/reports/{id}")
    public Result<ReportCatalog> getReport(@PathVariable Long id) {
        requireLogin();
        return Result.success(reportCatalogService.getReport(id));
    }

    @PostMapping("/reports/save-generated")
    public Result<Map<String, Object>> saveGenerated(@RequestBody Map<String, Object> body) {
        requireLogin();
        Object operator = body.remove("operator");
        return Result.success(reportCatalogService.saveGenerated(body,
                operator == null ? "system" : String.valueOf(operator)));
    }

    private Long longVal(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
