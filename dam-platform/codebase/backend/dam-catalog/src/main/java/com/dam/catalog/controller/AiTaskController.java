package com.dam.catalog.controller;

import com.dam.catalog.entity.AiTask;
import com.dam.catalog.entity.AiTaskItem;
import com.dam.catalog.service.AiRateLimiter;
import com.dam.catalog.service.AiTaskService;
import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
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
    private final AiRateLimiter aiRateLimiter;

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

    @GetMapping("/{id}/items")
    public Result<PageResult<AiTaskItem>> items(@PathVariable Long id,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "20") Integer pageSize,
                                                @RequestParam(required = false) String status) {
        return Result.success(taskService.itemPage(id, page, pageSize, status));
    }

    @PostMapping("/{id}/items/batch-review")
    public Result<Integer> batchReview(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        aiRateLimiter.check(aiRateLimiter.currentKey(), 60, 60_000);
        boolean accept = !Boolean.FALSE.equals(body.get("accept"))
                && !"false".equalsIgnoreCase(String.valueOf(body.get("accept")));
        boolean all = Boolean.TRUE.equals(body.get("all"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("all")));
        List<Long> ids = new ArrayList<>();
        if (body.get("itemIds") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) ids.add(n.longValue());
            }
        }
        return Result.success(taskService.reviewBatch(id, ids, accept, all, null));
    }

    @PostMapping
    public Result<AiTask> create(@RequestBody Map<String, Object> body) {
        aiRateLimiter.check(aiRateLimiter.currentKey(), 60, 60_000);
        return Result.success(taskService.create(body));
    }

    @PutMapping("/{taskId}/items/{itemId}")
    public Result<Void> updateItemDraft(@PathVariable Long taskId, @PathVariable Long itemId,
                                        @RequestBody Map<String, Object> body) {
        taskService.updateReportDraft(taskId, itemId, String.valueOf(body.getOrDefault("draftContent", "{}")));
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.deleteTask(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        taskService.updateStatus(id, body);
        return Result.success(null);
    }

    @PostMapping("/{id}/items/{itemId}/review")
    public Result<Void> review(@PathVariable Long id,
                               @PathVariable Long itemId,
                               @RequestParam(defaultValue = "true") boolean accept,
                               @RequestParam(required = false) String reviewer) {
        aiRateLimiter.check(aiRateLimiter.currentKey(), 120, 60_000);
        taskService.review(id, itemId, accept, reviewer);
        return Result.success(null);
    }
}
