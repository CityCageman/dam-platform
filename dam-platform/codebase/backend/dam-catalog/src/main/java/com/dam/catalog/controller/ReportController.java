package com.dam.catalog.controller;

import cn.hutool.core.util.StrUtil;
import com.dam.catalog.entity.AiTask;
import com.dam.catalog.service.AiTaskRunner;
import com.dam.catalog.service.AiTaskService;
import com.dam.catalog.service.ReportService;
import com.dam.common.exception.BusinessException;
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
    private final AiTaskService aiTaskService;
    private final AiTaskRunner aiTaskRunner;

    /**
     * 根据自然语言需求生成报表 SQL（调用编码智能体，结合数据源已同步表结构）
     *
     * @param params 请求体：requirement-报表需求、dataSourceId-数据源ID
     * @param token  当前登录 token（透传给 dam-model-gateway 鉴权）
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
     * 异步生成报表：创建 REPORT 任务，后台生成 SQL 并登记到报表体系。
     * 前端轮询任务进度，完成后可在「报表体系」查看。
     */
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
        // NEW 报表默认不携带历史 SQL，避免上一张报表的表名/口径污染当前需求。
        // 只有用户明确说“沿用/上次/之前/同一个/继续”，或 MODIFY 模式，才带历史上下文。
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
        Map<String, Object> taskBody = new java.util.LinkedHashMap<>();
        taskBody.put("taskType", "REPORT");
        taskBody.put("title", ("MODIFY".equals(mode) ? "AI 报表修改 · " : "AI 报表 · ") + title);
        taskBody.put("targetType", "DATASOURCE");
        taskBody.put("targetId", dataSourceId);
        taskBody.put("prompt", requirement);
        AiTask task = aiTaskService.create(taskBody);
        aiTaskRunner.runReport(task.getId(), requirement, dataSourceId, token, mode, reportId);
        return Result.success(Map.of("taskId", task.getId()));
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
        @SuppressWarnings("unchecked")
        Map<String, Object> queryParams = params.get("params") instanceof Map
                ? (Map<String, Object>) params.get("params") : Map.of();
        return Result.success(reportService.executeWithParams(dataSourceId, sql, queryParams, limit, false));
    }
}
