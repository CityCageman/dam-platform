package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.KnowledgeCategory;
import com.dam.catalog.entity.KnowledgeEntry;
import com.dam.catalog.entity.KnowledgeSyncConfig;
import com.dam.catalog.repository.KnowledgeCategoryMapper;
import com.dam.catalog.repository.KnowledgeEntryMapper;
import com.dam.catalog.repository.KnowledgeSyncConfigMapper;
import com.dam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * DAM 知识库与 WeKnora 的迁移/同步/检索适配。
 * 一个 WeKnora 知识库 + 本地分类映射为 WeKnora 标签。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeKnoraKnowledgeService {

    private final WeKnoraClient client;
    private final KnowledgeSyncConfigMapper configMapper;
    private final KnowledgeCategoryMapper categoryMapper;
    private final KnowledgeEntryMapper entryMapper;

    @Value("${dam.weknora.llm-model:qwen3-coder-30b-a3b}")
    private String preferredLlmModel;

    @Value("${dam.weknora.embedding-model:bge-m3}")
    private String preferredEmbeddingModel;

    private KnowledgeSyncConfig config() {
        KnowledgeSyncConfig c = configMapper.selectById(1L);
        if (c == null) {
            c = new KnowledgeSyncConfig();
            c.setId(1L);
            c.setAutoSync(0);
            c.setStatus(1);
            c.setCreatedAt(LocalDateTime.now());
            c.setCreatedBy("system");
            c.setUpdatedAt(LocalDateTime.now());
            c.setUpdatedBy("system");
            configMapper.insert(c);
        }
        return c;
    }

    /** 确保外部知识库存在；未指定 ID 时自动创建并保存 */
    public KnowledgeSyncConfig ensureKnowledgeBase() {
        if (!client.isConfigured()) throw new BusinessException("未配置 DAM_WEKNORA_API_KEY");
        KnowledgeSyncConfig c = config();
        String embeddingId = preferredModelId("Embedding", preferredEmbeddingModel);
        String summaryId = preferredModelId("KnowledgeQA", preferredLlmModel);
        if (StrUtil.isNotBlank(c.getExternalKbId())) {
            try {
                JsonNode kb = client.getKnowledgeBase(c.getExternalKbId());
                if (kb != null) {
                    // 绑定用户指定的模型；已有 KB 也允许纠正为 DAM 配置的模型
                    String currentEmbedding = text(kb, "embedding_model_id");
                    String currentSummary = text(kb, "summary_model_id");
                    boolean needEmbedding = StrUtil.isNotBlank(embeddingId) && !embeddingId.equals(currentEmbedding);
                    boolean needSummary = StrUtil.isNotBlank(summaryId) && !summaryId.equals(currentSummary);
                    if (needEmbedding || needSummary) {
                        client.bindDefaultModels(c.getExternalKbId(),
                                needEmbedding ? embeddingId : null,
                                needSummary ? summaryId : null);
                        log.info("已为 WeKnora 知识库 {} 绑定模型：embedding={}, summary={}",
                                c.getExternalKbId(), embeddingId, summaryId);
                    }
                    return c;
                }
            } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                // 云端 KB 被手动删除：清空旧映射，继续走新建
                if (msg.contains("404") || msg.contains("不存在") || msg.contains("not found")) {
                    log.warn("WeKnora 知识库 {} 已不存在，将为 DAM 重新创建", c.getExternalKbId());
                    resetExternalMapping(c);
                } else {
                    log.warn("检查/修复 WeKnora 知识库模型失败: {}", msg);
                    return c;
                }
            }
        }
        if (StrUtil.isBlank(embeddingId) || StrUtil.isBlank(summaryId)) {
            throw new BusinessException("WeKnora 未找到指定模型：LLM=" + preferredLlmModel
                    + "，Embedding=" + preferredEmbeddingModel + "，请确认模型名称");
        }
        JsonNode data = client.createKnowledgeBase("DAM 数据资产知识库",
                "由 DAM 数据资产管理平台同步的知识条目", embeddingId, summaryId);
        String id = text(data, "id");
        if (StrUtil.isBlank(id)) throw new BusinessException("WeKnora 创建知识库未返回 id");
        c.setExternalKbId(id);
        c.setExternalKbName(text(data, "name"));
        c.setUpdatedAt(LocalDateTime.now());
        c.setUpdatedBy("system");
        configMapper.updateById(c);
        return c;
    }

    /** 分类 → WeKnora 标签 */
    public void syncCategory(KnowledgeCategory category) {
        if (!client.isConfigured() || category == null || category.getId() == null) return;
        try {
            KnowledgeSyncConfig c = ensureKnowledgeBase();
            String tagId = ensureTag(c.getExternalKbId(), category);
            if (!Objects.equals(tagId, category.getExternalTagId())) {
                category.setExternalTagId(tagId);
                categoryMapper.updateById(category);
            }
        } catch (Exception e) {
            log.warn("知识分类同步 WeKnora 失败 [{}]: {}", category.getCategoryName(), e.getMessage());
        }
    }

    private String ensureTag(String kbId, KnowledgeCategory category) {
        String path = StrUtil.blankToDefault(category.getCategoryPath(), category.getCategoryName());
        int sortOrder = tagSortOrder(category);
        List<JsonNode> tags = client.listTags(kbId);
        String savedId = category.getExternalTagId();
        for (JsonNode tag : tags) {
            if (StrUtil.isNotBlank(savedId) && savedId.equals(text(tag, "id"))) {
                if (!path.equals(text(tag, "name")) || tag.path("sort_order").asInt(-1) != sortOrder) {
                    client.updateTag(kbId, savedId, path, text(tag, "color"), sortOrder);
                }
                bindTag(category, tag);
                return savedId;
            }
        }
        for (JsonNode tag : tags) {
            if (sameTagName(path, text(tag, "name"))) {
                String id = text(tag, "id");
                client.updateTag(kbId, id, path, text(tag, "color"), sortOrder);
                bindTag(category, tag);
                return id;
            }
        }
        try {
            JsonNode created = client.createTag(kbId, path, null, sortOrder);
            bindTag(category, created);
            String id = text(created, "id");
            if (StrUtil.isBlank(id)) throw new BusinessException("WeKnora 创建标签未返回 id: " + path);
            return id;
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("已存在") || msg.contains("409") || msg.contains("exist") || msg.contains("conflict")) {
                JsonNode existing = client.findTag(kbId, path);
                if (existing != null) {
                    bindTag(category, existing);
                    return text(existing, "id");
                }
                throw new BusinessException("WeKnora 标签名称已存在，但当前知识库无法匹配到该标签："
                        + path + "，请检查该标签是否属于其他知识库");
            }
            throw e;
        }
    }

    /** 按分类树 DFS 生成 WeKnora 标签排序，保证父分类、子分类顺序一致 */
    private int tagSortOrder(KnowledgeCategory target) {
        List<KnowledgeCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeCategory>().isNull(KnowledgeCategory::getDeletedAt));
        Map<Long, List<KnowledgeCategory>> children = new HashMap<>();
        for (KnowledgeCategory c : all) {
            children.computeIfAbsent(c.getParentId() == null ? 0L : c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        for (List<KnowledgeCategory> list : children.values()) {
            list.sort(Comparator.comparing((KnowledgeCategory c) -> c.getSortOrder() == null ? 0 : c.getSortOrder())
                    .thenComparing(KnowledgeCategory::getId));
        }
        int[] index = {0};
        Integer found = dfsOrder(children, 0L, target.getId(), index);
        return found == null ? 0 : found * 10;
    }

    private Integer dfsOrder(Map<Long, List<KnowledgeCategory>> children, Long parentId,
                             Long targetId, int[] index) {
        for (KnowledgeCategory c : children.getOrDefault(parentId, List.of())) {
            index[0]++;
            if (c.getId().equals(targetId)) return index[0];
            Integer found = dfsOrder(children, c.getId(), targetId, index);
            if (found != null) return found;
        }
        return null;
    }

    private void bindTag(KnowledgeCategory category, JsonNode tag) {
        String id = text(tag, "id");
        String name = text(tag, "name");
        String color = text(tag, "color");
        boolean changed = !Objects.equals(id, category.getExternalTagId())
                || !Objects.equals(name, category.getExternalTagName())
                || !Objects.equals(color, category.getExternalTagColor());
        category.setExternalTagId(id);
        category.setExternalTagName(name);
        category.setExternalTagColor(color);
        if (changed) categoryMapper.updateById(category);
    }

    private boolean sameTagName(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    /** 迁移全部本地分类/条目；可重复执行，已有 externalDocId 走更新 */
    public Map<String, Object> migrate() {
        KnowledgeSyncConfig c = ensureKnowledgeBase();
        List<KnowledgeCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<KnowledgeCategory>()
                .isNull(KnowledgeCategory::getDeletedAt));
        int tagCount = 0;
        for (KnowledgeCategory category : categories) {
            String before = category.getExternalTagId();
            syncCategory(category);
            if (StrUtil.isBlank(before) && StrUtil.isNotBlank(category.getExternalTagId())) tagCount++;
        }
        List<KnowledgeEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .isNull(KnowledgeEntry::getDeletedAt)
                .eq(KnowledgeEntry::getEnabled, 1));
        int synced = 0, failed = 0, pending = 0;
        for (KnowledgeEntry entry : entries) {
            try {
                syncEntry(entry);
                if ("SYNCED".equals(entry.getSyncStatus())) synced++;
                else if ("FAILED".equals(entry.getSyncStatus())) failed++;
                else pending++;
            } catch (Exception e) {
                entry.setSyncStatus("FAILED");
                entry.setLastError(abbreviate(e.getMessage()));
                entry.setUpdatedAt(LocalDateTime.now());
                entryMapper.updateById(entry);
                failed++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledgeBaseId", c.getExternalKbId());
        result.put("categories", categories.size());
        result.put("newTags", tagCount);
        result.put("entries", entries.size());
        result.put("synced", synced);
        result.put("failed", failed);
        result.put("pending", pending);
        return result;
    }

    /** 单条同步；外部为主，本地保留兜底快照 */
    public void syncEntry(KnowledgeEntry entry) {
        if (!client.isConfigured() || entry == null || entry.getId() == null) return;
        KnowledgeSyncConfig c = ensureKnowledgeBase();
        KnowledgeCategory category = entry.getCategoryId() == null ? null : categoryMapper.selectById(entry.getCategoryId());
        String tagId = category == null ? null : ensureTag(c.getExternalKbId(), category);
        List<String> tagIds = StrUtil.isBlank(tagId) ? List.of() : List.of(tagId);
        String content = renderDocument(entry);
        String publishStatus = "DRAFT".equalsIgnoreCase(entry.getPublishStatus()) ? "draft" : "publish";
        JsonNode data;
        if (StrUtil.isBlank(entry.getExternalDocId())) {
            data = client.createManual(c.getExternalKbId(), entry.getTitle(), content, tagIds, publishStatus);
            entry.setExternalDocId(text(data, "id"));
        } else {
            data = client.updateManual(entry.getExternalDocId(), entry.getTitle(), content, publishStatus);
        }
        if (StrUtil.isBlank(entry.getExternalDocId())) {
            throw new BusinessException("WeKnora 未返回知识文档 ID");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("damEntryId", String.valueOf(entry.getId()));
        metadata.put("damSourceType", StrUtil.blankToDefault(entry.getSourceType(), "MANUAL"));
        metadata.put("damSourceKey", StrUtil.blankToDefault(entry.getSourceKey(), "entry:" + entry.getId()));
        metadata.put("damCategoryId", entry.getCategoryId() == null ? "" : String.valueOf(entry.getCategoryId()));
        metadata.put("entryType", StrUtil.blankToDefault(entry.getEntryType(), "TERM"));
        metadata.put("aliases", cut(StrUtil.blankToDefault(entry.getAliases(), ""), 1000));
        metadata.put("fieldMappings", cut(StrUtil.blankToDefault(entry.getFieldMappings(), ""), 1000));
        metadata.put("relatedMetrics", cut(StrUtil.blankToDefault(entry.getRelatedMetrics(), ""), 1000));
        metadata.put("sourceSystem", cut(StrUtil.blankToDefault(entry.getSourceSystem(), ""), 1000));
        metadata.put("owner", cut(StrUtil.blankToDefault(entry.getOwner(), ""), 1000));
        metadata.put("publishStatus", StrUtil.blankToDefault(entry.getPublishStatus(), "PUBLISHED"));
        metadata.put("version", entry.getVersion() == null ? "1" : String.valueOf(entry.getVersion()));
        try { client.updateCustomMetadata(entry.getExternalDocId(), metadata); }
        catch (Exception e) { log.warn("写入 WeKnora 来源元数据失败 [{}]: {}", entry.getId(), e.getMessage()); }

        entry.setExternalKbId(c.getExternalKbId());
        entry.setLastSyncedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        // 写入成功只代表 WeKnora 已接收；同步摘要/元数据/标签并刷新解析状态
        JsonNode remote = client.getKnowledge(entry.getExternalDocId());
        applyRemoteKnowledge(entry, remote);
        entryMapper.updateById(entry);
    }

    public void deleteExternal(KnowledgeEntry entry) {
        if (!client.isConfigured() || entry == null || StrUtil.isBlank(entry.getExternalDocId())) return;
        try { client.deleteKnowledge(entry.getExternalDocId()); }
        catch (Exception e) { log.warn("删除 WeKnora 知识失败 [{}]: {}", entry.getExternalDocId(), e.getMessage()); }
    }

    /** 外部检索优先，失败/无结果时回退本地快照 */
    public Map<String, Object> search(String query, Integer limit) {
        if (StrUtil.isBlank(query)) throw new BusinessException("查询内容不能为空");
        int topK = limit == null ? 10 : Math.clamp(limit, 1, 50);
        Map<String, Object> result = new LinkedHashMap<>();
        if (client.isConfigured()) {
            try {
                KnowledgeSyncConfig c = ensureKnowledgeBase();
                List<JsonNode> rows = client.search(query, c.getExternalKbId(), topK);
                List<Map<String, Object>> hits = new ArrayList<>();
                for (JsonNode row : rows) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("content", text(row, "content"));
                    hit.put("knowledgeId", text(row, "knowledge_id"));
                    hit.put("title", text(row, "knowledge_title"));
                    hit.put("score", row.path("score").asDouble(0));
                    hit.put("knowledgeBaseId", text(row, "knowledge_base_id"));
                    hits.add(hit);
                }
                if (!hits.isEmpty()) {
                    result.put("source", "WEKNORA");
                    result.put("results", hits);
                    return result;
                }
            } catch (Exception e) {
                log.warn("WeKnora 检索失败，回退本地知识库: {}", e.getMessage());
                result.put("externalError", e.getMessage());
            }
        }
        List<KnowledgeEntry> local = entryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .isNull(KnowledgeEntry::getDeletedAt)
                .eq(KnowledgeEntry::getEnabled, 1)
                .and(w -> w.like(KnowledgeEntry::getTitle, query).or().like(KnowledgeEntry::getContent, query))
                .last("LIMIT " + topK));
        List<Map<String, Object>> hits = new ArrayList<>();
        for (KnowledgeEntry e : local) {
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("content", e.getContent());
            hit.put("knowledgeId", e.getExternalDocId());
            hit.put("title", e.getTitle());
            hit.put("score", 1.0);
            hit.put("knowledgeBaseId", e.getExternalKbId());
            hits.add(hit);
        }
        result.put("source", "LOCAL");
        result.put("results", hits);
        return result;
    }

    /** 外部 KB 被删除后，清掉旧 KB/旧 tag/旧文档 ID，避免继续更新已不存在的文档 */
    private void resetExternalMapping(KnowledgeSyncConfig c) {
        c.setExternalKbId(null);
        c.setExternalKbName(null);
        c.setUpdatedAt(LocalDateTime.now());
        c.setUpdatedBy("system");
        configMapper.updateById(c);
        List<KnowledgeCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeCategory>().isNull(KnowledgeCategory::getDeletedAt));
        for (KnowledgeCategory category : categories) {
            category.setExternalTagId(null);
            categoryMapper.updateById(category);
        }
        List<KnowledgeEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntry>().isNull(KnowledgeEntry::getDeletedAt));
        for (KnowledgeEntry entry : entries) {
            entry.setExternalKbId(null);
            entry.setExternalDocId(null);
            entry.setSyncStatus("LOCAL_ONLY");
            entry.setLastError(null);
            entry.setLastSyncedAt(null);
            entry.setUpdatedAt(LocalDateTime.now());
            entryMapper.updateById(entry);
        }
    }

    /**
     * 后台轮询解析中的文档，自动把 WeKnora parse_status 回写 DAM。
     * 默认 30 秒一次；只处理 PENDING，避免反复请求失败/已完成文档。
     */
    @Scheduled(fixedDelayString = "${dam.weknora.status-poll-ms:30000}",
            initialDelayString = "${dam.weknora.status-poll-initial-ms:60000}")
    public void pollPendingStatuses() {
        if (!client.isConfigured()) return;
        List<KnowledgeEntry> pending = entryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .isNull(KnowledgeEntry::getDeletedAt)
                .eq(KnowledgeEntry::getSyncStatus, "PENDING")
                .isNotNull(KnowledgeEntry::getExternalDocId)
                .orderByAsc(KnowledgeEntry::getId)
                .last("LIMIT 50"));
        for (KnowledgeEntry entry : pending) {
            try {
                refreshEntryStatus(entry.getId());
            } catch (Exception e) {
                log.warn("轮询 WeKnora 知识状态失败 [{}]: {}", entry.getId(), e.getMessage());
            }
        }
    }

    /** 查询单条知识的 WeKnora 解析/索引状态并回写 */
    public Map<String, Object> refreshEntryStatus(Long entryId) {
        KnowledgeEntry entry = entryMapper.selectById(entryId);
        if (entry == null || entry.getDeletedAt() != null) throw new BusinessException("知识条目不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entryId", entryId);
        if (StrUtil.isBlank(entry.getExternalDocId())) {
            result.put("syncStatus", "LOCAL_ONLY");
            result.put("embeddingStatus", entry.getEmbeddingStatus());
            result.put("lastError", null);
            return result;
        }
        try {
            applyParseStatus(entry, client.getKnowledge(entry.getExternalDocId()));
            entry.setUpdatedAt(LocalDateTime.now());
            entryMapper.updateById(entry);
        } catch (Exception e) {
            entry.setSyncStatus("FAILED");
            entry.setLastError(abbreviate(e.getMessage()));
            entry.setUpdatedAt(LocalDateTime.now());
            entryMapper.updateById(entry);
        }
        result.put("syncStatus", entry.getSyncStatus());
        result.put("embeddingStatus", entry.getEmbeddingStatus());
        result.put("externalDocId", entry.getExternalDocId());
        result.put("lastError", entry.getLastError());
        result.put("lastSyncedAt", entry.getLastSyncedAt());
        return result;
    }

    /** 主动刷新 WeKnora 解析状态；对失败的文档可重新解析 */
    public Map<String, Object> refreshStatuses(boolean reparseFailed) {
        List<KnowledgeEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .isNull(KnowledgeEntry::getDeletedAt)
                .isNotNull(KnowledgeEntry::getExternalDocId));
        int synced = 0, pending = 0, failed = 0, reparsed = 0;
        for (KnowledgeEntry entry : entries) {
            try {
                JsonNode doc = client.getKnowledge(entry.getExternalDocId());
                applyParseStatus(entry, doc);
                if ("FAILED".equals(entry.getSyncStatus()) && reparseFailed) {
                    client.reparseKnowledge(entry.getExternalDocId());
                    entry.setSyncStatus("PENDING");
                    entry.setLastError(null);
                    reparsed++;
                }
                entry.setUpdatedAt(LocalDateTime.now());
                entryMapper.updateById(entry);
                if ("SYNCED".equals(entry.getSyncStatus())) synced++;
                else if ("FAILED".equals(entry.getSyncStatus())) failed++;
                else pending++;
            } catch (Exception e) {
                entry.setLastError(abbreviate(e.getMessage()));
                entry.setUpdatedAt(LocalDateTime.now());
                entryMapper.updateById(entry);
                failed++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", entries.size());
        result.put("synced", synced);
        result.put("pending", pending);
        result.put("failed", failed);
        result.put("reparsed", reparsed);
        return result;
    }

    /** 结构化知识渲染为 WeKnora Markdown，保证 RAG 能读到完整上下文 */
    private String renderDocument(KnowledgeEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(StrUtil.blankToDefault(entry.getTitle(), "")).append("\n\n");
        sb.append("## 类型\n").append(typeLabel(entry.getEntryType())).append("\n\n");
        if (StrUtil.isNotBlank(entry.getDefinition())) {
            sb.append("## 解释\n").append(entry.getDefinition()).append("\n\n");
        }
        if (StrUtil.isNotBlank(entry.getUsageNote())) {
            sb.append("## 使用说明\n").append(entry.getUsageNote()).append("\n\n");
        }
        appendJsonList(sb, "别名", entry.getAliases());
        appendJsonList(sb, "字段映射", entry.getFieldMappings());
        appendJsonList(sb, "关联指标", entry.getRelatedMetrics());
        if (StrUtil.isNotBlank(entry.getSourceSystem())) {
            sb.append("## 来源系统\n").append(entry.getSourceSystem()).append("\n\n");
        }
        if (StrUtil.isNotBlank(entry.getOwner())) {
            sb.append("## 负责人\n").append(entry.getOwner()).append("\n\n");
        }
        sb.append("## 状态\n").append(StrUtil.blankToDefault(entry.getPublishStatus(), "PUBLISHED")).append("\n");
        if (StrUtil.isNotBlank(entry.getContent())) {
            sb.append("\n## 补充说明\n").append(entry.getContent()).append("\n");
        }
        return sb.toString();
    }

    private void appendJsonList(StringBuilder sb, String title, String json) {
        if (StrUtil.isBlank(json)) return;
        try {
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node.isArray()) {
                sb.append("## ").append(title).append("\n");
                for (JsonNode item : node) sb.append("- ").append(item.asText()).append("\n");
                sb.append("\n");
            } else {
                sb.append("## ").append(title).append("\n").append(node.asText()).append("\n\n");
            }
        } catch (Exception e) {
            sb.append("## ").append(title).append("\n").append(json).append("\n\n");
        }
    }

    private String typeLabel(String type) {
        return switch (StrUtil.blankToDefault(type, "TERM").toUpperCase()) {
            case "METRIC" -> "指标解释";
            case "STANDARD" -> "标准与编码";
            case "CASE" -> "案例与变更";
            default -> "业务术语";
        };
    }

    /** 把 WeKnora 文档的摘要、custom_metadata、tags、parse_status 同步回 DAM */
    private void applyRemoteKnowledge(KnowledgeEntry entry, JsonNode doc) {
        if (doc == null) return;
        entry.setExternalSummary(cut(text(doc, "description"), 20000));
        JsonNode custom = doc.get("custom_metadata");
        if (custom != null && !custom.isNull()) entry.setExternalMetadata(custom.toString());
        JsonNode tags = doc.get("tags");
        if (tags != null && !tags.isNull()) entry.setExternalTags(tags.toString());
        entry.setExternalParseStatus(text(doc, "parse_status"));
        entry.setExternalUpdatedAt(LocalDateTime.now());
        applyParseStatus(entry, doc);
    }

    private void applyParseStatus(KnowledgeEntry entry, JsonNode doc) {
        String parseStatus = doc == null ? null : text(doc, "parse_status");
        entry.setExternalParseStatus(parseStatus);
        entry.setExternalUpdatedAt(LocalDateTime.now());
        String status = StrUtil.blankToDefault(parseStatus, "pending").toLowerCase();
        if ("completed".equals(status) || "success".equals(status)) {
            entry.setSyncStatus("SYNCED");
            entry.setEmbeddingStatus("SYNCED");
            // 用空串而不是 null：MyBatis-Plus updateById 默认不会更新 null 字段
            entry.setLastError("");
            entry.setLastSyncedAt(LocalDateTime.now());
        } else if ("failed".equals(status) || "error".equals(status)) {
            entry.setSyncStatus("FAILED");
            entry.setEmbeddingStatus("FAILED");
            String err = text(doc, "error") != null ? text(doc, "error") : text(doc, "parse_error");
            entry.setLastError(abbreviate(err));
        } else {
            entry.setSyncStatus("PENDING");
            entry.setEmbeddingStatus("PENDING");
            entry.setLastError("");
        }
    }

    public Map<String, Object> status() {
        KnowledgeSyncConfig c = config();
        List<KnowledgeEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .isNull(KnowledgeEntry::getDeletedAt));
        long synced = entries.stream().filter(e -> "SYNCED".equals(e.getSyncStatus())).count();
        long failed = entries.stream().filter(e -> "FAILED".equals(e.getSyncStatus())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", client.isConfigured());
        result.put("baseUrl", client.getBaseUrl());
        result.put("externalKbId", c.getExternalKbId());
        result.put("externalKbName", c.getExternalKbName());
        result.put("total", entries.size());
        result.put("synced", synced);
        result.put("failed", failed);
        result.put("pending", entries.size() - synced - failed);
        return result;
    }

    /** 按用户指定名称选模型；找不到名称时回退 is_default，再回退同类型第一个 */
    private String preferredModelId(String type, String preferredName) {
        String fallbackDefault = null;
        String fallbackAny = null;
        for (JsonNode m : client.listModels()) {
            if (!type.equalsIgnoreCase(text(m, "type"))) continue;
            String id = text(m, "id");
            if (StrUtil.isBlank(id)) continue;
            if (fallbackAny == null) fallbackAny = id;
            String name = text(m, "name");
            String displayName = text(m, "display_name");
            if (StrUtil.isNotBlank(preferredName)
                    && (preferredName.equalsIgnoreCase(name) || preferredName.equalsIgnoreCase(displayName))) {
                return id;
            }
            if (m.path("is_default").asBoolean(false) && fallbackDefault == null) fallbackDefault = id;
        }
        return fallbackDefault != null ? fallbackDefault : fallbackAny;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    private String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private String abbreviate(String value) {
        if (value == null) return "未知错误";
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
