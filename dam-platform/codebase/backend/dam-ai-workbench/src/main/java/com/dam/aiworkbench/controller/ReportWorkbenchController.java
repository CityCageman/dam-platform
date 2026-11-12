package com.dam.aiworkbench.controller;

import cn.hutool.core.util.StrUtil;
import com.dam.aiworkbench.entity.AiTask;
import com.dam.aiworkbench.service.AiTaskRunner;
import com.dam.aiworkbench.service.AiTaskService;
import com.dam.aiworkbench.service.CatalogToolClient;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 报表生成编排接口。
 * 生成逻辑在 catalog 领域工具中执行，工作台负责任务、审核和历史上下文。
 */
@RestController
@RequestMapping("/api/ai-workbench/reports")
@RequiredArgsConstructor
public class ReportWorkbenchController {

    private final CatalogToolClient catalogTool;
    private final AiTaskService aiTaskService;
    private final AiTaskRunner aiTaskRunner;

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> params,
                                                @RequestHeader(value = "token", required = false) String token) {
        String requirement = (String) params.get("requirement");
        Long dataSourceId = params.get("dataSourceId") != null
                ? ((Number) params.get("dataSourceId")).longValue() : null;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requirement", requirement);
        body.put("dataSourceId", dataSourceId);
        body.put("token", token);
        return Result.success(catalogTool.generateReport(body));
    }

    @PostMapping("/generate/async")
    public Result<Map<String, Object>> generateAsync(@RequestBody Map<String, Object> params,
                                                     @RequestHeader(value = "token", required = false) String token) {
        String requirement = (String) params.get("requirement");
        if (requirement == null || requirement.isBlank()) throw new BusinessException("报表需求不能为空");
        Long dataSourceId = params.get("dataSourceId") != null
                ? ((Number) params.get("dataSourceId")).longValue() : null;
        if (dataSourceId == null) throw new BusinessException("请选择数据源");

        String mode = params.get("mode") == null ? "NEW" : String.valueOf(params.get("mode")).toUpperCase();
        Long reportId = params.get("reportId") != null ? ((Number) params.get("reportId")).longValue() : null;
        String baseSql = params.get("baseSql") == null ? null : String.valueOf(params.get("baseSql"));
        String history = params.get("history") == null ? null : String.valueOf(params.get("history"));
        boolean reuseContext = "MODIFY".equals(mode)
                || requirement.matches("(?s).*(沿用|上次|之前|同一个|继续).*");
        if (reuseContext) {
            String memory = aiTaskService.recentReportMemory();
            if (StrUtil.isNotBlank(memory) || StrUtil.isNotBlank(history)) {
                StringBuilder ctx = new StringBuilder("历史报表上下文（复用已确认的口径、字段和关联；不要重复问用户）：\n");
                if (StrUtil.isNotBlank(memory)) ctx.append(memory).append("\n");
                if (StrUtil.isNotBlank(history)) {
                    ctx.append("本次对话：\n")
                            .append(history.length() > 4000 ? history.substring(history.length() - 4000) : history)
                            .append("\n");
                }
                requirement = ctx + "当前需求：" + requirement;
            }
        }
        if ("MODIFY".equals(mode)) {
            if (reportId == null) throw new BusinessException("修改报表缺少报表ID");
            if (baseSql == null || baseSql.isBlank()) throw new BusinessException("修改报表缺少上一版SQL");
            requirement = "这是对上一版报表的修改请求，不要生成另一张无关报表。\n"
                    + "上一版 SQL：\n" + baseSql + "\n修改意见：" + requirement;
        }

        String title = params.get("displayRequirement") == null ? requirement : String.valueOf(params.get("displayRequirement"));
        if (title.length() > 180) title = title.substring(0, 180) + "…";
        Map<String, Object> taskBody = new LinkedHashMap<>();
        taskBody.put("taskType", "REPORT");
        taskBody.put("title", ("MODIFY".equals(mode) ? "AI 报表修改 · " : "AI 报表 · ") + title);
        taskBody.put("targetType", "DATASOURCE");
        taskBody.put("targetId", dataSourceId);
        taskBody.put("prompt", requirement);
        AiTask task = aiTaskService.create(taskBody);
        aiTaskRunner.runReport(task.getId(), requirement, dataSourceId, token, mode, reportId);
        return Result.success(Map.of("taskId", task.getId()));
    }
}
