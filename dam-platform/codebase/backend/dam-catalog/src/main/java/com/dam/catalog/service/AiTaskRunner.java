package com.dam.catalog.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.AiTask;
import com.dam.catalog.entity.AiTaskItem;
import com.dam.catalog.entity.ReportCatalog;
import com.dam.catalog.repository.AiTaskItemMapper;
import com.dam.catalog.repository.AiTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 后台任务执行器
 * <p>
 * 数据源级注释/分类由前端创建任务后异步执行；每批仍走原有 AI 服务（草稿模式），
 * 执行进度写入 ai_task，前端轮询任务状态即可。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskRunner {

    private static final int BATCH_SIZE = 40;
    private static final int MAX_BATCHES = 100;

    private final MetadataAiService metadataAiService;
    private final AssetClassifyAiService assetClassifyAiService;
    private final ReportService reportService;
    private final ReportCatalogService reportCatalogService;
    private final AiTaskMapper taskMapper;
    private final AiTaskItemMapper itemMapper;

    /**
     * 异步执行数据源级 AI 注释
     */
    @Async("aiTaskExecutor")
    public void runAnnotate(Long taskId, Long dataSourceId, boolean pendingOnly, String token) {
        run(taskId, "COMMENT", cursor -> {
            Map<String, Object> res = metadataAiService.annotate(
                    dataSourceId, pendingOnly, cursor, BATCH_SIZE, token, true, taskId);
            return res;
        });
    }

    /**
     * 异步执行数据源级 AI 分类
     */
    @Async("aiTaskExecutor")
    public void runClassify(Long taskId, Long dataSourceId, boolean pendingOnly, String token) {
        run(taskId, "CLASSIFY", cursor -> assetClassifyAiService.classify(
                dataSourceId, pendingOnly, cursor, BATCH_SIZE, token, true, taskId));
    }

    /**
     * 异步生成报表：AI 生成 SQL 后自动登记到报表体系，并写入任务明细。
     * 任务进度用 totalCount=1 / acceptedCount=0→1 表示。
     */
    @Async("aiTaskExecutor")
    public void runReport(Long taskId, String requirement, Long dataSourceId, String token, String mode, Long baseReportId) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        try {
            task.setStatus("RUNNING");
            task.setTotalCount(1);
            task.setAcceptedCount(0);
            task.setRejectedCount(0);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            Map<String, Object> generated = reportService.generate(requirement, dataSourceId, token);
            String sql = String.valueOf(generated.getOrDefault("sql", ""));
            if (sql.isBlank()) throw new IllegalStateException("AI 未生成有效 SQL");

            boolean modify = "MODIFY".equalsIgnoreCase(mode);
            ReportCatalog existing = modify ? reportCatalogService.getReport(baseReportId) : null;
            String reportName = existing != null ? existing.getReportName() : ("AI报表｜" + requirement).trim();
            if (reportName == null || reportName.isBlank()) reportName = "AI报表｜" + taskId;
            if (reportName.length() > 200) reportName = reportName.substring(0, 200);
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            if (existing != null) body.put("id", existing.getId());
            body.put("reportCode", existing != null ? existing.getReportCode() : "AI_RPT_" + taskId);
            body.put("reportName", reportName);
            body.put("reportTypeId", "REPORT");
            body.put("execType", "SQL");
            body.put("dataSourceId", dataSourceId);
            body.put("sqlContent", sql);
            String description = (modify ? "AI 修改" : "AI 生成") + "｜任务 #" + taskId + "｜" + requirement;
            if (description.length() > 500) description = description.substring(0, 500);
            body.put("description", description);
            body.put("owner", task.getCreatedBy());
            body.put("status", 1);
            Map<String, Object> saved = reportCatalogService.saveGenerated(body, task.getCreatedBy());
            Number reportId = saved.get("id") instanceof Number n ? n : null;
            Number assetId = saved.get("assetId") instanceof Number n ? n : null;

            Map<String, Object> draft = new java.util.LinkedHashMap<>();
            draft.put("reportId", reportId);
            draft.put("assetId", assetId);
            draft.put("reportName", reportName);
            draft.put("requirement", requirement);
            draft.put("dataSourceId", dataSourceId);
            draft.put("dataSourceName", generated.get("dataSourceName"));
            draft.put("sql", sql);
            draft.put("tablesUsed", generated.get("tablesUsed"));
            String draftJson = JSONUtil.toJsonStr(draft);
            if (draftJson.length() > 20000) draftJson = draftJson.substring(0, 20000);

            AiTaskItem item = new AiTaskItem();
            item.setTaskId(taskId);
            item.setItemType("REPORT");
            item.setTargetId(reportId == null ? null : reportId.longValue());
            item.setTargetName(reportName.length() > 200 ? reportName.substring(0, 200) : reportName);
            item.setDraftContent(draftJson);
            item.setStatus("ACCEPTED");
            item.setReviewedBy(task.getCreatedBy());
            item.setReviewedAt(LocalDateTime.now());
            item.setCreatedAt(LocalDateTime.now());
            itemMapper.insert(item);

            task.setStatus("DONE");
            task.setTotalCount(1);
            task.setAcceptedCount(1);
            task.setRejectedCount(0);
            task.setErrorMessage(null);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("AI 报表任务执行失败 [taskId={}]: {}", taskId, e.getMessage());
            task.setStatus("FAILED");
            task.setTotalCount(1);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            task.setErrorMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    /**
     * 通用批处理循环：按游标推进，直到 hasMore=false 或达到批次数上限
     */
    private void run(Long taskId, String taskType, BatchCall call) {
        Long cursor = null;
        int batches = 0;
        try {
            while (batches < MAX_BATCHES) {
                batches++;
                Map<String, Object> res = call.execute(cursor);
                int processed = intVal(res.get("processed"));
                Object next = res.get("nextCursor");
                cursor = next instanceof Number n ? n.longValue() : cursor;
                updateProgress(taskId);
                boolean hasMore = Boolean.TRUE.equals(res.get("hasMore"))
                        || "true".equalsIgnoreCase(String.valueOf(res.get("hasMore")));
                if (!hasMore || processed == 0) {
                    break;
                }
            }
            finish(taskId);
        } catch (Exception e) {
            log.warn("AI 任务执行失败 [taskId={}, type={}]: {}", taskId, taskType, e.getMessage());
            fail(taskId, e);
        }
    }

    private void updateProgress(Long taskId) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        task.setStatus("RUNNING");
        task.setTotalCount((int) itemCount(taskId));
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void finish(Long taskId) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        long total = itemCount(taskId);
        task.setTotalCount((int) total);
        task.setStatus(total > 0 ? "REVIEW" : "DONE");
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void fail(Long taskId, Exception e) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        task.setStatus("FAILED");
        task.setTotalCount((int) itemCount(taskId));
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        task.setErrorMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private long itemCount(Long taskId) {
        return itemMapper.selectCount(new LambdaQueryWrapper<AiTaskItem>()
                .eq(AiTaskItem::getTaskId, taskId));
    }

    private int intVal(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    @FunctionalInterface
    private interface BatchCall {
        Map<String, Object> execute(Long cursor) throws Exception;
    }
}
