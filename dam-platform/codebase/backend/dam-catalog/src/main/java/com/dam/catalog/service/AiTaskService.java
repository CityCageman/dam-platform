package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.catalog.entity.AiTask;
import com.dam.catalog.entity.AiTaskItem;
import com.dam.catalog.repository.AiTaskItemMapper;
import com.dam.catalog.repository.AiTaskMapper;
import com.dam.common.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 任务中心服务
 * <p>
 * 任务（ai_task）+ 草稿明细（ai_task_item）：AI 生成结果先落草稿，
 * 人工采纳/驳回后由业务模块回写资产字段；任务中心只负责流程与状态。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class AiTaskService {

    /** 单页上限，避免 pageSize 过大拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;
    /** 单任务手写草稿上限 */
    private static final int MAX_ITEMS = 1000;
    /** 单次批量审核上限 */
    private static final int MAX_BATCH_REVIEW = 5000;
    /** 草稿内容长度上限 */
    private static final int MAX_DRAFT_LEN = 20000;
    /** 合法任务类型/状态 */
    private static final java.util.Set<String> TASK_TYPES = java.util.Set.of("COMMENT", "CLASSIFY", "LINEAGE", "REPORT");
    private static final java.util.Set<String> STATUSES = java.util.Set.of("PENDING", "RUNNING", "REVIEW", "DONE", "FAILED");

    private final AiTaskMapper taskMapper;
    private final AiTaskItemMapper itemMapper;
    private final MetadataAiService metadataAiService;
    private final AssetClassifyAiService assetClassifyAiService;

    /**
     * 分页查询任务
     */
    public PageResult<AiTask> page(String status, String taskType, String keyword,
                                   Integer page, Integer pageSize) {
        int p = Math.max(1, page == null ? 1 : page);
        int size = Math.clamp(pageSize == null ? 20 : pageSize, 1, MAX_PAGE_SIZE);
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(AiTask::getStatus, status);
        }
        if (StrUtil.isNotBlank(taskType)) {
            wrapper.eq(AiTask::getTaskType, taskType);
        }
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.length() > 100 ? keyword.substring(0, 100) : keyword;
            wrapper.and(w -> w.like(AiTask::getTitle, kw)
                    .or().like(AiTask::getTargetType, kw));
        }
        wrapper.orderByDesc(AiTask::getId);
        return PageResult.of(taskMapper.selectPage(new Page<>(p, size), wrapper));
    }

    /**
     * 任务详情（含草稿明细）
     */
    public Map<String, Object> detail(Long id) {
        AiTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + id);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        return result;
    }

    /**
     * 任务明细分页
     */
    public PageResult<AiTaskItem> itemPage(Long taskId, Integer page, Integer pageSize, String status) {
        int p = Math.max(1, page == null ? 1 : page);
        int size = Math.clamp(pageSize == null ? 20 : pageSize, 1, MAX_PAGE_SIZE);
        LambdaQueryWrapper<AiTaskItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiTaskItem::getTaskId, taskId);
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(AiTaskItem::getStatus, status);
        }
        wrapper.orderByAsc(AiTaskItem::getId);
        return PageResult.of(itemMapper.selectPage(new Page<>(p, size), wrapper));
    }

    /**
     * 批量采纳/驳回
     *
     * @param taskId  任务ID
     * @param itemIds 指定明细（all=true 时忽略）
     * @param accept  true=采纳，false=驳回
     * @param all     true=处理该任务全部 DRAFT 明细
     * @param reviewer 审核人
     * @return 处理条数
     */
    @Transactional
    public int reviewBatch(Long taskId, List<Long> itemIds, boolean accept, boolean all, String reviewer) {
        LambdaQueryWrapper<AiTaskItem> wrapper = new LambdaQueryWrapper<AiTaskItem>()
                .eq(AiTaskItem::getTaskId, taskId);
        if (all) {
            wrapper.eq(AiTaskItem::getStatus, "DRAFT");
        } else if (itemIds != null && !itemIds.isEmpty()) {
            if (itemIds.size() > MAX_BATCH_REVIEW) {
                throw new IllegalArgumentException("单次批量审核不能超过 " + MAX_BATCH_REVIEW + " 条，请分批处理");
            }
            wrapper.in(AiTaskItem::getId, itemIds);
        } else {
            return 0;
        }
        List<AiTaskItem> items = itemMapper.selectList(wrapper);
        if (items.size() > MAX_BATCH_REVIEW) {
            throw new IllegalArgumentException("单次批量审核不能超过 " + MAX_BATCH_REVIEW + " 条，请分批处理");
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int count = 0;
        for (AiTaskItem item : items) {
            if (!"DRAFT".equals(item.getStatus())) {
                continue;
            }
            if (accept) {
                applyDraft(item);
            }
            item.setStatus(accept ? "ACCEPTED" : "REJECTED");
            item.setReviewedBy(StrUtil.isNotBlank(reviewer) ? reviewer : "system");
            item.setReviewedAt(now);
            itemMapper.updateById(item);
            count++;
        }
        refreshTask(taskId);
        return count;
    }

    /**
     * 创建任务（可由 AI 生成流程调用，items 为草稿列表）
     */
    @Transactional
    public AiTask create(Map<String, Object> body) {
        String title = str(body.get("title"));
        if (StrUtil.isBlank(title)) {
            throw new IllegalArgumentException("任务标题不能为空");
        }
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }
        String taskType = StrUtil.isNotBlank(str(body.get("taskType"))) ? str(body.get("taskType")) : "COMMENT";
        if (!TASK_TYPES.contains(taskType)) {
            throw new IllegalArgumentException("不支持的任务类型: " + taskType);
        }
        String status = StrUtil.isNotBlank(str(body.get("status"))) ? str(body.get("status")) : "PENDING";
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("不支持的任务状态: " + status);
        }
        LocalDateTime now = LocalDateTime.now();
        AiTask task = new AiTask();
        task.setTaskType(taskType);
        task.setTitle(title);
        task.setTargetType(cut(str(body.get("targetType")), 32));
        task.setTargetId(toLong(body.get("targetId")));
        task.setStatus(status);
        task.setTotalCount(0);
        task.setAcceptedCount(0);
        task.setRejectedCount(0);
        task.setModel(cut(str(body.get("model")), 100));
        task.setPrompt(cut(str(body.get("prompt")), 20000));
        task.setCreatedBy(cut(StrUtil.isNotBlank(str(body.get("createdBy"))) ? str(body.get("createdBy")) : "system", 100));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        int total = 0;
        Object itemsObj = body.get("items");
        if (itemsObj instanceof List<?> items) {
            if (items.size() > MAX_ITEMS) {
                throw new IllegalArgumentException("单个任务草稿不能超过 " + MAX_ITEMS + " 条");
            }
            for (Object o : items) {
                if (!(o instanceof Map<?, ?> item)) continue;
                AiTaskItem row = new AiTaskItem();
                row.setTaskId(task.getId());
                row.setItemType(cut(str(item.get("itemType")), 32));
                row.setTargetId(toLong(item.get("targetId")));
                row.setTargetName(cut(str(item.get("targetName")), 200));
                row.setFieldName(cut(str(item.get("fieldName")), 100));
                String draft = str(item.get("draftContent"));
                row.setDraftContent(draft != null && draft.length() > MAX_DRAFT_LEN
                        ? draft.substring(0, MAX_DRAFT_LEN) : draft);
                Object conf = item.get("confidence");
                if (conf != null && StrUtil.isNotBlank(String.valueOf(conf))) {
                    try {
                        row.setConfidence(new BigDecimal(String.valueOf(conf)));
                    } catch (NumberFormatException ignored) {
                        row.setConfidence(null);
                    }
                }
                row.setStatus("DRAFT");
                row.setCreatedAt(now);
                itemMapper.insert(row);
                total++;
            }
        }
        if (total > 0) {
            task.setTotalCount(total);
            if ("PENDING".equals(task.getStatus())) {
                task.setStatus("REVIEW");
            }
            task.setUpdatedAt(now);
            taskMapper.updateById(task);
        }
        return task;
    }

    /**
     * 采纳/驳回一条草稿，并更新任务进度
     */
    @Transactional
    public void review(Long taskId, Long itemId, boolean accept, String reviewer) {
        AiTaskItem item = itemMapper.selectById(itemId);
        if (item == null || !taskId.equals(item.getTaskId())) {
            throw new IllegalArgumentException("任务明细不存在或不属于该任务");
        }
        if (accept) {
            applyDraft(item);
        }
        LocalDateTime now = LocalDateTime.now();
        item.setStatus(accept ? "ACCEPTED" : "REJECTED");
        item.setReviewedBy(StrUtil.isNotBlank(reviewer) ? reviewer : "system");
        item.setReviewedAt(now);
        itemMapper.updateById(item);

        refreshTask(taskId);
    }

    /**
     * 重新汇总任务进度并更新状态
     */
    private void refreshTask(Long taskId) {
        long accepted = itemMapper.selectCount(new LambdaQueryWrapper<AiTaskItem>()
                .eq(AiTaskItem::getTaskId, taskId).eq(AiTaskItem::getStatus, "ACCEPTED"));
        long rejected = itemMapper.selectCount(new LambdaQueryWrapper<AiTaskItem>()
                .eq(AiTaskItem::getTaskId, taskId).eq(AiTaskItem::getStatus, "REJECTED"));
        long total = itemMapper.selectCount(new LambdaQueryWrapper<AiTaskItem>()
                .eq(AiTaskItem::getTaskId, taskId));

        AiTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        task.setAcceptedCount((int) accepted);
        task.setRejectedCount((int) rejected);
        task.setTotalCount((int) total);
        task.setStatus(accepted + rejected >= total ? "DONE" : "REVIEW");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 更新任务状态/进度（AI 运行中或结束后回写）
     *
     * @param id   任务ID
     * @param body status/totalCount/acceptedCount/rejectedCount/errorMessage
     */
    @Transactional
    public void updateStatus(Long id, Map<String, Object> body) {
        AiTask task = taskMapper.selectById(id);
        if (task == null) {
            return;
        }
        String status = str(body.get("status"));
        if (StrUtil.isNotBlank(status)) {
            if (!STATUSES.contains(status)) {
                throw new IllegalArgumentException("不支持的任务状态: " + status);
            }
            task.setStatus(status);
        }
        Integer total = toInt(body.get("totalCount"));
        if (total != null) task.setTotalCount(Math.max(0, total));
        Integer accepted = toInt(body.get("acceptedCount"));
        if (accepted != null) task.setAcceptedCount(Math.max(0, accepted));
        Integer rejected = toInt(body.get("rejectedCount"));
        if (rejected != null) task.setRejectedCount(Math.max(0, rejected));
        if (body.containsKey("errorMessage")) {
            task.setErrorMessage(cut(str(body.get("errorMessage")), 500));
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 采纳时把草稿写回业务表
     *
     * @param item 草稿明细
     */
    private void applyDraft(AiTaskItem item) {
        if (StrUtil.isBlank(item.getDraftContent())) {
            throw new IllegalArgumentException("草稿内容为空，无法采纳");
        }
        JSONObject json = JSONUtil.parseObj(item.getDraftContent());
        if ("COMMENT".equalsIgnoreCase(item.getItemType())) {
            metadataAiService.applyAnnotationDraft(item.getTargetId(),
                    json.getStr("tableComment"), json.getStr("subjectDomain"), json.getStr("securityLevel"));
        } else if ("CLASSIFY".equalsIgnoreCase(item.getItemType())) {
            assetClassifyAiService.applyClassificationDraft(item.getTargetId(), json.getLong("categoryId"));
        }
    }

    /**
     * 任务状态统计
     */
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", taskMapper.selectCount(new LambdaQueryWrapper<>()));
        for (String status : List.of("PENDING", "RUNNING", "REVIEW", "DONE", "FAILED")) {
            result.put(status, taskMapper.selectCount(new LambdaQueryWrapper<AiTask>()
                    .eq(AiTask::getStatus, status)));
        }
        return result;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 按数据库列长度截断，避免超长写入报错 */
    private String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        try {
            return Long.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        try {
            return Integer.valueOf(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
