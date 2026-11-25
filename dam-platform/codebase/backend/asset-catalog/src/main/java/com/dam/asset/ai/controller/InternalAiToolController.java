package com.dam.asset.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.asset.ai.service.AssetClassifyAiService;
import com.dam.asset.ai.service.MetadataAiService;
import com.dam.platform.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 供 ai-workbench 调用的资产 AI 领域工具接口。
 * <p>
 * 这些接口不直接面向浏览器，避免 AI 服务直接访问 catalog 数据库。
 * 原实现位于 analytics-report，报表模块脱离 asset-catalog 依赖后迁移到本服务，
 * 对外 URL 保持不变（/api/catalog/internal/ai/annotate 等）。
 */
@RestController
@RequestMapping("/api/catalog/internal/ai")
@RequiredArgsConstructor
public class InternalAiToolController {

    private final MetadataAiService metadataAiService;
    private final AssetClassifyAiService assetClassifyAiService;

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
