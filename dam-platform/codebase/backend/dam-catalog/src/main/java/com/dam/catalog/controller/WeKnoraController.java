package com.dam.catalog.controller;

import com.dam.catalog.service.WeKnoraKnowledgeService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** WeKnora 知识库迁移/检索接口 */
@RestController
@RequestMapping("/api/catalog/knowledge/external")
@RequiredArgsConstructor
public class WeKnoraController {

    private final WeKnoraKnowledgeService weKnoraKnowledgeService;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(weKnoraKnowledgeService.status());
    }

    /** 初始化/修复 WeKnora 知识库的默认模型绑定 */
    @PostMapping("/initialize")
    public Result<Map<String, Object>> initialize() {
        weKnoraKnowledgeService.ensureKnowledgeBase();
        return Result.success(weKnoraKnowledgeService.status());
    }

    /** 全量迁移；可重复执行，已迁移条目走更新 */
    @PostMapping("/migrate")
    public Result<Map<String, Object>> migrate() {
        return Result.success(weKnoraKnowledgeService.migrate());
    }

    @PostMapping("/search")
    public Result<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? "" : String.valueOf(body.get("query"));
        Integer limit = body.get("limit") instanceof Number n ? n.intValue() : null;
        return Result.success(weKnoraKnowledgeService.search(query, limit));
    }
}
