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
        if (StrUtil.isNotBlank(category.getExternalTagId())) return category.getExternalTagId();
        for (JsonNode tag : client.listTags(kbId)) {
            if (category.getCategoryName().equals(text(tag, "name"))) return text(tag, "id");
        }
        JsonNode created = client.createTag(kbId, category.getCategoryName());
        return text(created, "id");
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
        int synced = 0, failed = 0;
        for (KnowledgeEntry entry : entries) {
            try {
                syncEntry(entry);
                if ("SYNCED".equals(entry.getSyncStatus())) synced++; else failed++;
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
        return result;
    }

    /** 单条同步；外部为主，本地保留兜底快照 */
    public void syncEntry(KnowledgeEntry entry) {
        if (!client.isConfigured() || entry == null || entry.getId() == null) return;
        KnowledgeSyncConfig c = ensureKnowledgeBase();
        KnowledgeCategory category = entry.getCategoryId() == null ? null : categoryMapper.selectById(entry.getCategoryId());
        String tagId = category == null ? null : ensureTag(c.getExternalKbId(), category);
        List<String> tagIds = StrUtil.isBlank(tagId) ? List.of() : List.of(tagId);
        String content = StrUtil.blankToDefault(entry.getContent(), entry.getTitle());
        JsonNode data;
        if (StrUtil.isBlank(entry.getExternalDocId())) {
            data = client.createManual(c.getExternalKbId(), entry.getTitle(), content, tagIds);
            entry.setExternalDocId(text(data, "id"));
        } else {
            data = client.updateManual(entry.getExternalDocId(), entry.getTitle(), content);
        }
        if (StrUtil.isBlank(entry.getExternalDocId())) {
            throw new BusinessException("WeKnora 未返回知识文档 ID");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("damEntryId", String.valueOf(entry.getId()));
        metadata.put("damSourceType", StrUtil.blankToDefault(entry.getSourceType(), "MANUAL"));
        metadata.put("damSourceKey", StrUtil.blankToDefault(entry.getSourceKey(), "entry:" + entry.getId()));
        metadata.put("damCategoryId", entry.getCategoryId() == null ? "" : String.valueOf(entry.getCategoryId()));
        try { client.updateCustomMetadata(entry.getExternalDocId(), metadata); }
        catch (Exception e) { log.warn("写入 WeKnora 来源元数据失败 [{}]: {}", entry.getId(), e.getMessage()); }

        entry.setExternalKbId(c.getExternalKbId());
        entry.setSyncStatus("SYNCED");
        entry.setLastSyncedAt(LocalDateTime.now());
        entry.setLastError(null);
        entry.setEmbeddingStatus("SYNCED");
        entry.setUpdatedAt(LocalDateTime.now());
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

    private String abbreviate(String value) {
        if (value == null) return "未知错误";
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
