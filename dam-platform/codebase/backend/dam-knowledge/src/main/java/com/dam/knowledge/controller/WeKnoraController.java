package com.dam.knowledge.controller;

import com.dam.knowledge.service.WeKnoraKnowledgeService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * WeKnora 知识库迁移/检索接口。
 * <p>
 * 所有接口统一前缀 {@code /api/knowledge/external}，由独立的 dam-knowledge 服务提供。
 */
@RestController
@RequestMapping("/api/knowledge/external")
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

    @GetMapping("/entries/{entryId}/status")
    public Result<Map<String, Object>> entryStatus(@PathVariable Long entryId) {
        return Result.success(weKnoraKnowledgeService.refreshEntryStatus(entryId));
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody(required = false) Map<String, Object> body) {
        boolean reparseFailed = body != null && (Boolean.TRUE.equals(body.get("reparseFailed"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("reparseFailed"))));
        return Result.success(weKnoraKnowledgeService.refreshStatuses(reparseFailed));
    }

    @PostMapping("/search")
    public Result<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? "" : String.valueOf(body.get("query"));
        Integer limit = body.get("limit") instanceof Number n ? n.intValue() : null;
        return Result.success(weKnoraKnowledgeService.search(query, limit));
    }
}
