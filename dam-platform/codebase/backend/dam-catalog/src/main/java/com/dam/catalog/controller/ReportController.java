package com.dam.catalog.controller;

import com.dam.catalog.service.ReportService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 报表智能生成与执行接口
 * <p>
 * 提供「自然语言需求 → 生成报表 SQL」与「安全执行 SQL 返回结果集」两个接口，
 * 供前端智能助手（聊天）在报表模式下调用。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/catalog/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 根据自然语言需求生成报表 SQL（调用编码智能体，结合数据源已同步表结构）
     *
     * @param params 请求体：requirement-报表需求、dataSourceId-数据源ID
     * @param token  当前登录 token（透传给 dam-agent 鉴权）
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> params,
                                                @RequestHeader(value = "token", required = false) String token) {
        String requirement = (String) params.get("requirement");
        Long dataSourceId = params.get("dataSourceId") != null
                ? ((Number) params.get("dataSourceId")).longValue() : null;
        return Result.success(reportService.generate(requirement, dataSourceId, token));
    }

    /**
     * 安全执行报表 SQL（仅 SELECT）并返回结果集
     *
     * @param params 请求体：dataSourceId-数据源ID、sql-SELECT语句、limit-最大返回行数（可选）
     */
    @PostMapping("/execute")
    public Result<Map<String, Object>> execute(@RequestBody Map<String, Object> params) {
        Long dataSourceId = params.get("dataSourceId") != null
                ? ((Number) params.get("dataSourceId")).longValue() : null;
        String sql = (String) params.get("sql");
        Integer limit = params.get("limit") != null
                ? ((Number) params.get("limit")).intValue() : null;
        return Result.success(reportService.execute(dataSourceId, sql, limit));
    }
}
