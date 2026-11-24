package com.dam.asset.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.asset.report.entity.ReportCatalog;
import com.dam.asset.ai.service.AssetClassifyAiService;
import com.dam.asset.ai.service.MetadataAiService;
import com.dam.asset.metric.entity.MetricDefinition;
import com.dam.asset.report.service.ReportCatalogService;
import com.dam.asset.report.service.ReportService;
import com.dam.platform.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供 dam-ai-workbench 调用的领域工具接口。
 * 这些接口不直接面向浏览器，避免 AI 服务直接访问 catalog 数据库。
 */
@RestController
@RequestMapping("/api/catalog/internal/ai")
@RequiredArgsConstructor
public class InternalAiToolController {

    private final MetadataAiService metadataAiService;
    private final AssetClassifyAiService assetClassifyAiService;
    private final ReportService reportService;
    private final ReportCatalogService reportCatalogService;

    private void requireLogin() {
        StpUtil.checkLogin();
    }

    @PostMapping("/annotate")
    public Result<Map<String, Object>> annotate(@RequestBody Map<String, Object> body) {
        requireLogin();
        return Result.success(metadataAiService.annotate(
                longVal(body.get("dataSourceId")),
                Boolean.TRUE.equals(body.get("pendingOnly")),
                longVal(body.get("cursor")),
                intVal(body.get("batchSize")),
                String.valueOf(body.getOrDefault("token", "")),
                true,
                longVal(body.get("taskId"))));
    }

    @PostMapping("/classify")
    public Result<Map<String, Object>> classify(@RequestBody Map<String, Object> body) {
        requireLogin();
        return Result.success(assetClassifyAiService.classify(
                longVal(body.get("dataSourceId")),
                Boolean.TRUE.equals(body.get("pendingOnly")),
                longVal(body.get("cursor")),
                intVal(body.get("batchSize")),
                String.valueOf(body.getOrDefault("token", "")),
                true,
                longVal(body.get("taskId"))));
    }

    @PostMapping("/apply-annotation")
    public Result<Void> applyAnnotation(@RequestBody Map<String, Object> body) {
        requireLogin();
        metadataAiService.applyAnnotationDraft(
                longVal(body.get("tableId")),
                str(body.get("tableComment")),
                str(body.get("subjectDomain")),
                str(body.get("securityLevel")));
        return Result.success(null);
    }

    @PostMapping("/apply-classification")
    public Result<Void> applyClassification(@RequestBody Map<String, Object> body) {
        requireLogin();
        assetClassifyAiService.applyClassificationDraft(
                longVal(body.get("assetId")), longVal(body.get("categoryId")));
        return Result.success(null);
    }

    /**
     * 供 dam-metric 调用的指标 SQL 生成接口。
     * <p>
     * 指标主数据由 dam-metric 独占，catalog 只负责复用元数据/知识库/模型网关能力生成 SQL，
     * 不落库、不读写 dam_metric 表。
     *
     * @param metric 指标定义（dam-metric 传入的 DTO）
     * @param token  当前登录 token（缺省时从 Sa-Token 上下文读取）
     * @return {sql, dataSourceName, tablesUsed, params}
     */
    @PostMapping("/metrics/sql")
    public Result<Map<String, Object>> generateMetricSql(
            @RequestBody MetricDefinition metric,
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

    private int intVal(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
