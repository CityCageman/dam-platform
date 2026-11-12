package com.dam.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.AiTask;
import com.dam.catalog.repository.AiTaskItemMapper;
import com.dam.catalog.entity.AiTaskItem;
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
