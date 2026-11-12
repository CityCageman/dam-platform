package com.dam.catalog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.catalog.entity.ReportCatalog;
import com.dam.catalog.service.AssetClassifyAiService;
import com.dam.catalog.service.MetadataAiService;
import com.dam.catalog.service.ReportCatalogService;
import com.dam.catalog.service.ReportService;
import com.dam.common.vo.Result;
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
