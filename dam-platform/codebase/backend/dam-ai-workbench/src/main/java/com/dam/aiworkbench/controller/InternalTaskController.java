package com.dam.aiworkbench.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.aiworkbench.entity.AiTask;
import com.dam.aiworkbench.service.AiTaskRunner;
import com.dam.aiworkbench.service.AiTaskService;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供 catalog 领域服务调用的任务中心内部接口。
 * 只接受携带 token 的服务间调用，不让 catalog 直接写 dam_ai schema。
 */
@RestController
@RequestMapping("/api/ai-workbench/internal")
@RequiredArgsConstructor
public class InternalTaskController {

    private final AiTaskService taskService;
    private final AiTaskRunner taskRunner;

    @GetMapping("/tasks/{id}")
    public Result<AiTask> task(@PathVariable Long id) {
        StpUtil.checkLogin();
        return Result.success(taskService.getOwnedTask(id));
    }

    @PostMapping("/tasks/{taskId}/items")
    public Result<Void> addItem(@PathVariable Long taskId, @RequestBody Map<String, Object> body) {
        StpUtil.checkLogin();
        taskService.addDraftItem(taskId,
                str(body.get("itemType")),
                longVal(body.get("targetId")),
                str(body.get("targetName")),
                str(body.get("draftContent")));
        return Result.success(null);
    }

    @PostMapping("/run/annotate")
    public Result<Void> runAnnotate(@RequestBody Map<String, Object> body) {
        StpUtil.checkLogin();
        Long taskId = longVal(body.get("taskId"));
        taskRunner.runAnnotate(taskId, longVal(body.get("dataSourceId")),
                Boolean.TRUE.equals(body.get("pendingOnly")), str(body.get("token")));
        return Result.success(null);
    }

    @PostMapping("/run/classify")
    public Result<Void> runClassify(@RequestBody Map<String, Object> body) {
        StpUtil.checkLogin();
        Long taskId = longVal(body.get("taskId"));
        taskRunner.runClassify(taskId, longVal(body.get("dataSourceId")),
                Boolean.TRUE.equals(body.get("pendingOnly")), str(body.get("token")));
        return Result.success(null);
    }

    private Long longVal(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
