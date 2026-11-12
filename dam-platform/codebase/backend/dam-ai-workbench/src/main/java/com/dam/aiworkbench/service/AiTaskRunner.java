package com.dam.aiworkbench.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.aiworkbench.entity.AiTask;
import com.dam.aiworkbench.entity.AiTaskItem;
import com.dam.aiworkbench.repository.AiTaskItemMapper;
import com.dam.aiworkbench.repository.AiTaskMapper;
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

    private final CatalogToolClient catalogTool;
    private final AiTaskMapper taskMapper;
    private final AiTaskItemMapper itemMapper;

    /**
     * 异步执行数据源级 AI 注释
     */
    @Async("aiTaskExecutor")
    public void runAnnotate(Long taskId, Long dataSourceId, boolean pendingOnly, String token) {
        run(taskId, "COMMENT", cursor -> {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("dataSourceId", dataSourceId);
            body.put("pendingOnly", pendingOnly);
            body.put("cursor", cursor);
            body.put("batchSize", BATCH_SIZE);
            body.put("token", token);
            body.put("taskId", taskId);
            return catalogTool.annotate(body);
        });
    }

    /**
     * 异步执行数据源级 AI 分类
     */
    @Async("aiTaskExecutor")
    public void runClassify(Long taskId, Long dataSourceId, boolean pendingOnly, String token) {
        run(taskId, "CLASSIFY", cursor -> {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("dataSourceId", dataSourceId);
            body.put("pendingOnly", pendingOnly);
            body.put("cursor", cursor);
            body.put("batchSize", BATCH_SIZE);
            body.put("token", token);
            body.put("taskId", taskId);
            return catalogTool.classify(body);
        });
    }

    /**
     * 异步生成报表：AI 生成 SQL 后自动登记到报表体系，并写入任务明细。
     * 任务进度用 totalCount=1 / acceptedCount=0→1 表示。
     */
    @Async("aiTaskExecutor")
    public void runReport(Long taskId, String requirement, Long dataSourceId, String token, String mode, Long baseReportId) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null || "CANCELLED".equals(task.getStatus())) return;
        try {
            task.setStatus("RUNNING");
            task.setTotalCount(1);
            task.setAcceptedCount(0);
            task.setRejectedCount(0);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("requirement", requirement);
            request.put("dataSourceId", dataSourceId);
            request.put("token", token);
            Map<String, Object> generated = catalogTool.generateReport(request);
            if (isCancelled(taskId)) return;
            String sql = String.valueOf(generated.getOrDefault("sql", ""));
            if (sql.isBlank()) throw new IllegalStateException("AI 未生成有效 SQL");

            if (isCancelled(taskId)) return;
            boolean modify = "MODIFY".equalsIgnoreCase(mode);
            String title = catalogTool.generateTitle(requirement, token);
            if (isCancelled(taskId)) return;
            if (title == null || title.isBlank()) title = "AI报表 " + taskId;
            if (title.length() > 200) title = title.substring(0, 200);

            // 只生成草稿，确认后才由 AiTaskService.applyDraft 写入 report_catalog
            Map<String, Object> draft = new java.util.LinkedHashMap<>();
            draft.put("reportName", title);
            draft.put("requirement", requirement);
            draft.put("dataSourceId", dataSourceId);
            draft.put("dataSourceName", generated.get("dataSourceName"));
            draft.put("sql", sql);
            draft.put("tablesUsed", generated.get("tablesUsed"));
            draft.put("knowledgeUsed", generated.get("knowledgeUsed"));
            draft.put("params", generated.get("params"));
            draft.put("drillSql", generated.get("drillSql"));
            draft.put("drillParams", generated.get("drillParams"));
            draft.put("drillError", generated.get("drillError"));
            draft.put("mode", modify ? "MODIFY" : "NEW");
            draft.put("baseReportId", baseReportId);
            String draftJson = JSONUtil.toJsonStr(draft);
            if (draftJson.length() > 20000) draftJson = draftJson.substring(0, 20000);

            AiTaskItem item = new AiTaskItem();
            item.setTaskId(taskId);
            item.setItemType("REPORT");
            item.setTargetId(baseReportId);
            item.setTargetName(title);
            item.setDraftContent(draftJson);
            item.setStatus("DRAFT");
            item.setCreatedAt(LocalDateTime.now());
            itemMapper.insert(item);

            task.setTitle(("MODIFY".equals(mode) ? "AI 报表修改 · " : "AI 报表 · ") + title);
            task.setStatus("REVIEW");
            task.setTotalCount(1);
            task.setAcceptedCount(0);
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
                if (isCancelled(taskId)) return;
                batches++;
                Map<String, Object> res = call.execute(cursor);
                if (isCancelled(taskId)) return;
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
        if (task == null || "CANCELLED".equals(task.getStatus())) return;
        task.setStatus("RUNNING");
        task.setTotalCount((int) itemCount(taskId));
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void finish(Long taskId) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null || "CANCELLED".equals(task.getStatus())) return;
        long total = itemCount(taskId);
        task.setTotalCount((int) total);
        task.setStatus(total > 0 ? "REVIEW" : "DONE");
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void fail(Long taskId, Exception e) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null || "CANCELLED".equals(task.getStatus())) return;
        task.setStatus("FAILED");
        task.setTotalCount((int) itemCount(taskId));
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        task.setErrorMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private boolean isCancelled(Long taskId) {
        AiTask task = taskMapper.selectById(taskId);
        return task == null || "CANCELLED".equals(task.getStatus());
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
