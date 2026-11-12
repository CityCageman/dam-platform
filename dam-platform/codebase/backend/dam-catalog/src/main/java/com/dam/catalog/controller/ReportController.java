package com.dam.catalog.controller;

import com.dam.catalog.service.ReportService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 报表 SQL 安全执行接口。
 * AI 报表生成编排已迁移到 dam-ai-workbench，catalog 只保留领域执行能力。
 */
@RestController
@RequestMapping("/api/catalog/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 安全执行报表 SQL，返回结果集。
     */
    @PostMapping("/execute")
    public Result<Map<String, Object>> execute(@RequestBody Map<String, Object> params) {
        Long dataSourceId = params.get("dataSourceId") != null
                ? ((Number) params.get("dataSourceId")).longValue() : null;
        String sql = (String) params.get("sql");
        Integer limit = params.get("limit") != null
                ? ((Number) params.get("limit")).intValue() : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> queryParams = params.get("params") instanceof Map
                ? (Map<String, Object>) params.get("params") : Map.of();
        return Result.success(reportService.executeWithParams(dataSourceId, sql, queryParams, limit, false));
    }
}
