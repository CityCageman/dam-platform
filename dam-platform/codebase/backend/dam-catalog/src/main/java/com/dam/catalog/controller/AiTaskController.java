package com.dam.catalog.controller;

import com.dam.catalog.entity.AiTask;
import com.dam.catalog.service.AiTaskService;
import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 任务中心接口
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/catalog/ai/tasks")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiTaskService taskService;

    @GetMapping
    public Result<PageResult<AiTask>> page(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String taskType,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(taskService.page(status, taskType, keyword, page, pageSize));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(taskService.stats());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(taskService.detail(id));
    }

    @PostMapping
    public Result<AiTask> create(@RequestBody Map<String, Object> body) {
        return Result.success(taskService.create(body));
    }

    @PostMapping("/{id}/items/{itemId}/review")
    public Result<Void> review(@PathVariable Long id,
                               @PathVariable Long itemId,
                               @RequestParam(defaultValue = "true") boolean accept,
                               @RequestParam(required = false) String reviewer) {
        taskService.review(id, itemId, accept, reviewer);
        return Result.success(null);
    }
}
