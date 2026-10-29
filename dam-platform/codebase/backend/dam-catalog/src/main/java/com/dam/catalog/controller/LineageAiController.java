package com.dam.catalog.controller;

import com.dam.catalog.service.LineageAiService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 数据血缘识别接口
 * <p>
 * 提供「SQL 输入 → AI 识别表级 + 字段级血缘」能力，供前端血缘管理页在预览确认后落库。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/catalog/lineage")
@RequiredArgsConstructor
public class LineageAiController {

    private final LineageAiService lineageAiService;

    /**
     * AI 识别 SQL 的数据血缘（表级 + 字段级分开返回）
     *
     * @param params 请求体：sql-SQL语句、dataSourceId-数据源ID（可选，限定表结构检索范围）
     * @param token  当前登录 token（透传给 dam-agent 鉴权）
     */
    @PostMapping("/ai-recognize")
    public Result<Map<String, Object>> recognize(@RequestBody Map<String, Object> params,
                                                 @RequestHeader(value = "token", required = false) String token) {
        String sql = (String) params.get("sql");
        Long dataSourceId = params.get("dataSourceId") != null
                ? ((Number) params.get("dataSourceId")).longValue() : null;
        return Result.success(lineageAiService.recognize(sql, dataSourceId, token));
    }

    /**
     * AI 识别数据源内各表之间的关联关系（无需 SQL，依据 _DR 指针命名约定 + 业务语义）
     *
     * @param params 请求体：dataSourceId-数据源ID
     * @param token  当前登录 token（透传给 dam-agent 鉴权）
     */
    @PostMapping("/ai-recognize-tables")
    public Result<Map<String, Object>> recognizeTableRelations(@RequestBody Map<String, Object> params,
                                                               @RequestHeader(value = "token", required = false) String token) {
        Long dataSourceId = params.get("dataSourceId") != null
                ? ((Number) params.get("dataSourceId")).longValue() : null;
        return Result.success(lineageAiService.recognizeTableRelations(dataSourceId, token));
    }
}
