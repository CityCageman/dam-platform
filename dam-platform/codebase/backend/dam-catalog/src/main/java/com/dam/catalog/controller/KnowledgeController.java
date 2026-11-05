package com.dam.catalog.controller;

import com.dam.catalog.entity.KnowledgeCategory;
import com.dam.catalog.entity.KnowledgeEntry;
import com.dam.catalog.service.KnowledgeService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库：分类/条目维护、选择性同步、向量库对接预留。
 */
@RestController
@RequestMapping("/api/catalog/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    // ==================== 分类 ====================
    @GetMapping("/categories")
    public Result<List<KnowledgeCategory>> categories() {
        return Result.success(knowledgeService.categories());
    }

    @PostMapping("/categories")
    public Result<KnowledgeCategory> createCategory(@RequestBody Map<String, Object> body) {
        body.remove("id");
        return Result.success(knowledgeService.saveCategory(body));
    }

    @PutMapping("/categories/{id}")
    public Result<KnowledgeCategory> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return Result.success(knowledgeService.saveCategory(body));
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        knowledgeService.deleteCategory(id);
        return Result.success(null);
    }

    // ==================== 条目 ====================
    @GetMapping("/entries")
    public Result<List<KnowledgeEntry>> entries(@RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String sourceType,
                                                @RequestParam(required = false) Integer limit) {
        return Result.success(knowledgeService.entries(categoryId, keyword, sourceType, limit));
    }

    @PostMapping("/entries")
    public Result<KnowledgeEntry> createEntry(@RequestBody Map<String, Object> body) {
        body.remove("id");
        return Result.success(knowledgeService.saveEntry(body));
    }

    @PutMapping("/entries/{id}")
    public Result<KnowledgeEntry> updateEntry(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return Result.success(knowledgeService.saveEntry(body));
    }

    @DeleteMapping("/entries/{id}")
    public Result<Void> deleteEntry(@PathVariable Long id) {
        knowledgeService.deleteEntry(id);
        return Result.success(null);
    }

    // ==================== 从其他模块选择性同步 ====================
    @GetMapping("/sources")
    public Result<List<Map<String, Object>>> sources(@RequestParam String sourceType,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer limit) {
        return Result.success(knowledgeService.sourceCandidates(sourceType, keyword, limit));
    }

    @PostMapping("/sync")
    public Result<Map<String, Object>> sync(@RequestBody Map<String, Object> body) {
        Long categoryId = body.get("categoryId") instanceof Number n ? n.longValue() : null;
        String sourceType = String.valueOf(body.getOrDefault("sourceType", ""));
        Object raw = body.get("sources");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = raw instanceof List ? (List<Map<String, Object>>) raw : List.of();
        int count = knowledgeService.sync(categoryId, sourceType, sources);
        return Result.success(Map.of("synced", count));
    }

    // ==================== 向量库预留 API ====================
    /** 向量服务拉取待索引文档：GET /api/catalog/knowledge/vector/pending */
    @GetMapping("/vector/pending")
    public Result<List<Map<String, Object>>> vectorPending(@RequestParam(required = false) Integer limit) {
        return Result.success(knowledgeService.pendingVectorDocuments(limit));
    }

    /** 单条文档：GET /api/catalog/knowledge/entries/{id}/vector-document */
    @GetMapping("/entries/{id}/vector-document")
    public Result<Map<String, Object>> vectorDocument(@PathVariable Long id) {
        return Result.success(knowledgeService.vectorDocument(id));
    }

    /** 向量服务回写：POST /api/catalog/knowledge/entries/{id}/vector-callback */
    @PostMapping("/entries/{id}/vector-callback")
    public Result<Void> vectorCallback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        knowledgeService.vectorCallback(id, body);
        return Result.success(null);
    }

    /** 重新入队：POST /api/catalog/knowledge/entries/{id}/vector-reindex */
    @PostMapping("/entries/{id}/vector-reindex")
    public Result<Void> reindex(@PathVariable Long id) {
        knowledgeService.reindex(id);
        return Result.success(null);
    }
}
