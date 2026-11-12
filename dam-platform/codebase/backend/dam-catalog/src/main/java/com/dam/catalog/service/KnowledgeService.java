package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.*;
import com.dam.catalog.repository.*;
import com.dam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库：可维护分类、条目、外部数据同步和向量索引预留接口。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeCategoryMapper categoryMapper;
    private final KnowledgeEntryMapper entryMapper;
    private final MetadataTableMapper tableMapper;
    private final MetadataSchemaMapper schemaMapper;
    private final MetricDefinitionMapper metricMapper;
    private final JdbcTemplate jdbcTemplate;
    private final WeKnoraKnowledgeService weKnoraKnowledgeService;

    public List<KnowledgeCategory> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<KnowledgeCategory>()
                .isNull(KnowledgeCategory::getDeletedAt)
                .orderByAsc(KnowledgeCategory::getCategoryPath)
                .orderByAsc(KnowledgeCategory::getSortOrder)
                .orderByAsc(KnowledgeCategory::getId));
    }

    @Transactional
    public KnowledgeCategory saveCategory(Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        String code = StrUtil.trimToEmpty(str(body.get("categoryCode")));
        String name = StrUtil.trimToEmpty(str(body.get("categoryName")));
        if (StrUtil.isBlank(code)) throw new BusinessException("请填写分类编码");
        if (StrUtil.isBlank(name)) throw new BusinessException("请填写分类名称");
        KnowledgeCategory c = id == null ? new KnowledgeCategory() : requireCategory(id);
        long dup = categoryMapper.selectCount(new LambdaQueryWrapper<KnowledgeCategory>()
                .eq(KnowledgeCategory::getCategoryCode, code)
                .isNull(KnowledgeCategory::getDeletedAt)
                .ne(id != null, KnowledgeCategory::getId, id));
        if (dup > 0) throw new BusinessException("分类编码已存在");

        Long parentId = toLong(body.get("parentId"));
        if (parentId != null && parentId == 0) parentId = null;
        if (parentId != null && id != null && parentId.equals(id)) throw new BusinessException("分类不能作为自己的父分类");
        KnowledgeCategory parent = parentId == null ? null : requireCategory(parentId);
        if (parent != null && id != null && isDescendant(parentId, id)) {
            throw new BusinessException("不能移动到自己的子分类下");
        }
        String oldPath = StrUtil.blankToDefault(c.getCategoryPath(), c.getCategoryName());
        int oldLevel = c.getCategoryLevel() == null ? 1 : c.getCategoryLevel();
        String parentPath = parent == null ? "" : StrUtil.blankToDefault(parent.getCategoryPath(), parent.getCategoryName());
        String newPath = StrUtil.isBlank(parentPath) ? name : parentPath + "/" + name;
        int newLevel = parent == null ? 1 : (parent.getCategoryLevel() == null ? 1 : parent.getCategoryLevel()) + 1;
        long pathDup = categoryMapper.selectCount(new LambdaQueryWrapper<KnowledgeCategory>()
                .eq(KnowledgeCategory::getCategoryPath, newPath)
                .isNull(KnowledgeCategory::getDeletedAt)
                .ne(id != null, KnowledgeCategory::getId, id));
        if (pathDup > 0) throw new BusinessException("分类路径已存在: " + newPath);

        String user = currentUser();
        LocalDateTime now = LocalDateTime.now();
        if (id == null) { c.setCreatedAt(now); c.setCreatedBy(user); }
        c.setCategoryCode(cut(code, 100));
        c.setCategoryName(cut(name, 200));
        c.setDescription(cut(str(body.get("description")), 500));
        c.setParentId(parentId);
        c.setCategoryPath(cut(newPath, 500));
        c.setCategoryLevel(newLevel);
        c.setSortOrder(body.get("sortOrder") instanceof Number n ? n.intValue() : 0);
        c.setStatus(body.get("status") instanceof Number n ? n.intValue() : 1);
        c.setUpdatedAt(now);
        c.setUpdatedBy(user);
        if (id == null) categoryMapper.insert(c); else categoryMapper.updateById(c);

        // 分类改名/移动时同步更新所有子孙路径
        if (id != null && StrUtil.isNotBlank(oldPath) && !oldPath.equals(newPath)) {
            List<KnowledgeCategory> descendants = categoryMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeCategory>()
                            .likeRight(KnowledgeCategory::getCategoryPath, oldPath + "/")
                            .isNull(KnowledgeCategory::getDeletedAt));
            for (KnowledgeCategory child : descendants) {
                String childPath = child.getCategoryPath();
                if (childPath != null && childPath.startsWith(oldPath + "/")) {
                    child.setCategoryPath(newPath + childPath.substring(oldPath.length()));
                    child.setCategoryLevel((child.getCategoryLevel() == null ? 1 : child.getCategoryLevel())
                            + (newLevel - oldLevel));
                    child.setUpdatedAt(now);
                    child.setUpdatedBy(user);
                    categoryMapper.updateById(child);
                    weKnoraKnowledgeService.syncCategory(child);
                }
            }
        }
        weKnoraKnowledgeService.syncCategory(c);
        return c;
    }

    private boolean isDescendant(Long childId, Long ancestorId) {
        Long cursor = childId;
        int guard = 0;
        while (cursor != null && guard++ < 50) {
            KnowledgeCategory node = categoryMapper.selectById(cursor);
            if (node == null || node.getDeletedAt() != null) return false;
            if (ancestorId.equals(node.getParentId())) return true;
            cursor = node.getParentId();
        }
        return false;
    }

    @Transactional
    public void deleteCategory(Long id) {
        KnowledgeCategory c = requireCategory(id);
        long children = categoryMapper.selectCount(new LambdaQueryWrapper<KnowledgeCategory>()
                .eq(KnowledgeCategory::getParentId, id).isNull(KnowledgeCategory::getDeletedAt));
        if (children > 0) throw new BusinessException("该分类下还有子分类，请先删除或移动子分类");
        long entries = entryMapper.selectCount(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getCategoryId, id).isNull(KnowledgeEntry::getDeletedAt));
        if (entries > 0) throw new BusinessException("该分类下还有 " + entries + " 条知识，请先删除或迁移");
        c.setDeletedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        c.setUpdatedBy(currentUser());
        categoryMapper.updateById(c);
    }

    public List<KnowledgeEntry> entries(Long categoryId, String keyword, String sourceType, Integer limit) {
        LambdaQueryWrapper<KnowledgeEntry> w = new LambdaQueryWrapper<KnowledgeEntry>()
                .isNull(KnowledgeEntry::getDeletedAt)
                .orderByDesc(KnowledgeEntry::getUpdatedAt);
        if (categoryId != null) w.eq(KnowledgeEntry::getCategoryId, categoryId);
        if (StrUtil.isNotBlank(keyword)) w.and(q -> q.like(KnowledgeEntry::getTitle, keyword)
                .or().like(KnowledgeEntry::getContent, keyword));
        if (StrUtil.isNotBlank(sourceType)) w.eq(KnowledgeEntry::getSourceType, sourceType);
        w.last("LIMIT " + Math.clamp(limit == null ? 200 : limit, 1, 500));
        return entryMapper.selectList(w);
    }

    public KnowledgeEntry saveEntry(Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        Long categoryId = toLong(body.get("categoryId"));
        if (categoryId == null) throw new BusinessException("请选择知识分类");
        requireCategory(categoryId);
        String title = StrUtil.trimToEmpty(str(body.get("title")));
        if (StrUtil.isBlank(title)) throw new BusinessException("请填写知识标题");
        KnowledgeEntry e = id == null ? new KnowledgeEntry() : requireEntry(id);
        String user = currentUser();
        LocalDateTime now = LocalDateTime.now();
        if (id == null) {
            e.setCreatedAt(now);
            e.setCreatedBy(user);
            e.setSourceType("MANUAL");
            e.setSyncStatus("LOCAL_ONLY");
        }
        e.setCategoryId(categoryId);
        e.setTitle(cut(title, 300));
        e.setContent(str(body.get("content")));
        e.setEntryType(StrUtil.blankToDefault(str(body.get("entryType")), "TERM").toUpperCase());
        e.setDefinition(str(body.get("definition")));
        e.setUsageNote(str(body.get("usageNote")));
        e.setAliases(jsonArray(body.get("aliases")));
        e.setFieldMappings(jsonArray(body.get("fieldMappings")));
        e.setRelatedMetrics(jsonArray(body.get("relatedMetrics")));
        e.setSqlExample(str(body.get("sqlExample")));
        Object reportSpec = body.get("reportSpec");
        e.setReportSpec(reportSpec == null ? null
                : (reportSpec instanceof String s ? s : JSONUtil.toJsonStr(reportSpec)));
        e.setSourceSystem(cut(str(body.get("sourceSystem")), 100));
        e.setOwner(cut(str(body.get("owner")), 100));
        e.setPublishStatus(StrUtil.blankToDefault(str(body.get("publishStatus")), "PUBLISHED").toUpperCase());
        e.setVersion(id == null ? 1 : (e.getVersion() == null ? 1 : e.getVersion() + 1));
        e.setEffectiveFrom(parseDateTime(str(body.get("effectiveFrom"))));
        e.setEffectiveTo(parseDateTime(str(body.get("effectiveTo"))));
        e.setSourceType(id == null ? "MANUAL" : StrUtil.blankToDefault(e.getSourceType(), "MANUAL"));
        e.setTags(body.get("tags") == null ? null : (body.get("tags") instanceof String s ? s : JSONUtil.toJsonStr(body.get("tags"))));
        e.setEmbeddingStatus("PENDING");
        e.setEnabled(body.get("enabled") instanceof Number n ? n.intValue() : 1);
        e.setUpdatedAt(now);
        e.setUpdatedBy(user);
        if (id == null) entryMapper.insert(e); else entryMapper.updateById(e);
        try {
            weKnoraKnowledgeService.syncEntry(e);
        } catch (Exception ex) {
            e.setSyncStatus("FAILED");
            e.setLastError(String.valueOf(ex.getMessage()).length() > 1000
                    ? String.valueOf(ex.getMessage()).substring(0, 1000) : ex.getMessage());
            e.setUpdatedAt(LocalDateTime.now());
            entryMapper.updateById(e);
        }
        return e;
    }

    public void deleteEntry(Long id) {
        KnowledgeEntry e = requireEntry(id);
        weKnoraKnowledgeService.deleteExternal(e);
        e.setDeletedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(currentUser());
        entryMapper.updateById(e);
    }

    /** 知识库维护概览：总数、索引状态、分类/来源分布 */
    public Map<String, Object> stats() {
        List<KnowledgeEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .isNull(KnowledgeEntry::getDeletedAt));
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        Map<String, Integer> bySource = new LinkedHashMap<>();
        long pending = 0, synced = 0, failed = 0;
        for (KnowledgeEntry e : entries) {
            byCategory.merge(String.valueOf(e.getCategoryId()), 1, Integer::sum);
            bySource.merge(StrUtil.blankToDefault(e.getSourceType(), "MANUAL"), 1, Integer::sum);
            if ("SYNCED".equals(e.getEmbeddingStatus())) synced++;
            else if ("FAILED".equals(e.getEmbeddingStatus())) failed++;
            else pending++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", entries.size());
        result.put("pending", pending);
        result.put("synced", synced);
        result.put("failed", failed);
        result.put("byCategory", byCategory);
        result.put("bySource", bySource);
        return result;
    }

    /** 可同步来源：只返回候选项，用户勾选后才进入知识库 */
    public List<Map<String, Object>> sourceCandidates(String sourceType, String keyword, Integer limit) {
        int size = Math.clamp(limit == null ? 100 : limit, 1, 300);
        String kw = StrUtil.trimToEmpty(keyword).toLowerCase();
        List<Map<String, Object>> result = new ArrayList<>();
        if ("METADATA".equalsIgnoreCase(sourceType)) {
            List<MetadataTable> tables = tableMapper.selectList(new LambdaQueryWrapper<MetadataTable>()
                    .last("LIMIT 500"));
            Set<Long> schemaIds = tables.stream().map(MetadataTable::getSchemaId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> schemaMap = new HashMap<>();
            if (!schemaIds.isEmpty()) schemaMapper.selectByIds(schemaIds).forEach(s -> schemaMap.put(s.getId(), s.getSchemaName()));
            for (MetadataTable t : tables) {
                String schema = schemaMap.getOrDefault(t.getSchemaId(), "");
                String title = (StrUtil.isNotBlank(schema) ? schema + "." : "") + t.getTableName();
                String desc = StrUtil.blankToDefault(t.getTableComment(), "");
                if (!kw.isEmpty() && !title.toLowerCase().contains(kw) && !desc.toLowerCase().contains(kw)) continue;
                result.add(Map.of("id", t.getId(), "title", title, "description", desc,
                        "sourceType", "METADATA", "sourceKey", "metadata:" + t.getId()));
                if (result.size() >= size) break;
            }
        } else if ("METRIC".equalsIgnoreCase(sourceType)) {
            List<MetricDefinition> metrics = metricMapper.selectList(new LambdaQueryWrapper<MetricDefinition>()
                    .isNull(MetricDefinition::getDeletedAt).last("LIMIT 500"));
            for (MetricDefinition m : metrics) {
                String desc = StrUtil.blankToDefault(m.getDefinition(), StrUtil.blankToDefault(m.getFormula(), ""));
                if (!kw.isEmpty() && !m.getMetricName().toLowerCase().contains(kw) && !desc.toLowerCase().contains(kw)) continue;
                result.add(Map.of("id", m.getId(), "title", m.getMetricName(), "description", desc,
                        "sourceType", "METRIC", "sourceKey", "metric:" + m.getId()));
                if (result.size() >= size) break;
            }
        } else if ("STANDARD".equalsIgnoreCase(sourceType)) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT id, code_table_name, description FROM dam_standard.standard_code_table WHERE status = 1 ORDER BY id DESC LIMIT 500");
                for (Map<String, Object> r : rows) {
                    String title = String.valueOf(r.getOrDefault("code_table_name", ""));
                    String desc = String.valueOf(r.getOrDefault("description", ""));
                    if (!kw.isEmpty() && !title.toLowerCase().contains(kw) && !desc.toLowerCase().contains(kw)) continue;
                    result.add(Map.of("id", ((Number) r.get("id")).longValue(), "title", title, "description", desc,
                            "sourceType", "STANDARD", "sourceKey", "standard:" + r.get("id")));
                    if (result.size() >= size) break;
                }
            } catch (Exception e) {
                throw new BusinessException("标准库读取失败: " + e.getMessage());
            }
        } else {
            throw new BusinessException("不支持的知识来源: " + sourceType);
        }
        return result;
    }

    /** 把用户勾选的来源写入知识库；不选就不会同步 */
    @Transactional
    public int sync(Long categoryId, String sourceType, List<Map<String, Object>> sources) {
        KnowledgeCategory category = requireCategory(categoryId);
        if (sources == null || sources.isEmpty()) throw new BusinessException("请选择要同步的内容");
        String user = currentUser();
        int count = 0;
        for (Map<String, Object> s : sources) {
            String sourceKey = str(s.get("sourceKey"));
            String title = str(s.get("title"));
            String description = str(s.get("description"));
            if (StrUtil.isBlank(sourceKey) || StrUtil.isBlank(title)) continue;
            KnowledgeEntry e = entryMapper.selectOne(new LambdaQueryWrapper<KnowledgeEntry>()
                    .eq(KnowledgeEntry::getSourceType, sourceType.toUpperCase())
                    .eq(KnowledgeEntry::getSourceKey, sourceKey)
                    .isNull(KnowledgeEntry::getDeletedAt).last("LIMIT 1"));
            boolean isNew = e == null;
            if (isNew) { e = new KnowledgeEntry(); e.setCreatedAt(LocalDateTime.now()); e.setCreatedBy(user); }
            e.setCategoryId(category.getId());
            e.setTitle(cut(title, 300));
            e.setContent(description);
            e.setSourceType(sourceType.toUpperCase());
            e.setSourceId(str(s.get("sourceId") != null ? s.get("sourceId") : s.get("id")));
            e.setSourceKey(cut(sourceKey, 300));
            e.setEmbeddingStatus("PENDING");
            e.setEnabled(1);
            e.setUpdatedAt(LocalDateTime.now());
            e.setUpdatedBy(user);
            if (isNew) entryMapper.insert(e); else entryMapper.updateById(e);
            count++;
        }
        return count;
    }

    // ==================== 向量库预留接口 ====================

    /** 外部向量化服务拉取待索引文档 */
    public List<Map<String, Object>> pendingVectorDocuments(Integer limit) {
        int size = Math.clamp(limit == null ? 100 : limit, 1, 500);
        List<KnowledgeEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .isNull(KnowledgeEntry::getDeletedAt)
                .eq(KnowledgeEntry::getEnabled, 1)
                .in(KnowledgeEntry::getEmbeddingStatus, List.of("PENDING", "FAILED"))
                .orderByAsc(KnowledgeEntry::getId)
                .last("LIMIT " + size));
        return entries.stream().map(this::vectorDocument).collect(Collectors.toList());
    }

    public Map<String, Object> vectorDocument(Long id) {
        return vectorDocument(requireEntry(id));
    }

    private Map<String, Object> vectorDocument(KnowledgeEntry e) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("entryId", e.getId());
        doc.put("title", e.getTitle());
        doc.put("content", StrUtil.blankToDefault(e.getContent(), e.getTitle()));
        doc.put("categoryId", e.getCategoryId());
        doc.put("sourceType", e.getSourceType());
        doc.put("sourceKey", e.getSourceKey());
        doc.put("tags", e.getTags());
        doc.put("vectorId", e.getVectorId());
        doc.put("embeddingStatus", e.getEmbeddingStatus());
        return doc;
    }

    /** 向量服务回写索引结果：SYNCED / FAILED */
    public void vectorCallback(Long id, Map<String, Object> body) {
        KnowledgeEntry e = requireEntry(id);
        String status = StrUtil.blankToDefault(str(body.get("status")), "").toUpperCase();
        if (!List.of("SYNCED", "FAILED").contains(status)) throw new BusinessException("status 只支持 SYNCED/FAILED");
        e.setVectorId(cut(str(body.get("vectorId")), 200));
        e.setEmbeddingStatus(status);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy("vector-service");
        entryMapper.updateById(e);
    }

    public void reindex(Long id) {
        KnowledgeEntry e = requireEntry(id);
        e.setEmbeddingStatus("PENDING");
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(currentUser());
        entryMapper.updateById(e);
    }

    private KnowledgeCategory requireCategory(Long id) {
        KnowledgeCategory c = id == null ? null : categoryMapper.selectById(id);
        if (c == null || c.getDeletedAt() != null) throw new BusinessException("知识分类不存在");
        return c;
    }

    private KnowledgeEntry requireEntry(Long id) {
        KnowledgeEntry e = id == null ? null : entryMapper.selectById(id);
        if (e == null || e.getDeletedAt() != null) throw new BusinessException("知识条目不存在");
        return e;
    }

    /** 数组/对象统一存 JSON；字符串按逗号拆成数组，兼容前端文本输入 */
    private String jsonArray(Object value) {
        if (value == null) return null;
        if (value instanceof String s) {
            String text = s.trim();
            if (text.isEmpty()) return null;
            if (text.startsWith("[") || text.startsWith("{")) return text;
            List<String> values = Arrays.stream(text.split("[,，\\n]"))
                    .map(String::trim).filter(v -> !v.isEmpty()).toList();
            return values.isEmpty() ? null : JSONUtil.toJsonStr(values);
        }
        return JSONUtil.toJsonStr(value);
    }

    private LocalDateTime parseDateTime(String value) {
        if (StrUtil.isBlank(value)) return null;
        try { return LocalDateTime.parse(value.trim().replace(" ", "T")); }
        catch (Exception e) { return null; }
    }

    private String currentUser() {
        try {
            Object id = cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull();
            return id == null ? "system" : String.valueOf(id);
        } catch (Exception e) { return "system"; }
    }
    private String str(Object v) { return v == null ? null : String.valueOf(v); }
    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v != null && StrUtil.isNotBlank(String.valueOf(v))) {
            try { return Long.valueOf(String.valueOf(v)); } catch (NumberFormatException ignored) { }
        }
        return null;
    }
    private String cut(String v, int max) { return v == null || v.length() <= max ? v : v.substring(0, max); }
}
