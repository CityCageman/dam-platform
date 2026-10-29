package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.AssetCategoryRel;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.entity.DamCategory;
import com.dam.catalog.entity.MetadataColumn;
import com.dam.catalog.entity.MetadataTable;
import com.dam.catalog.repository.AssetCategoryRelMapper;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.catalog.repository.DamCategoryMapper;
import com.dam.catalog.repository.MetadataColumnMapper;
import com.dam.catalog.repository.MetadataTableMapper;
import com.dam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 资产分类 AI 智能识别服务
 * <p>
 * 扫描表同步到资产目录后，调用 dam-agent 编码智能体，根据表名、表注释、主题域与字段结构，
 * 从平台已有资产分类（asset_category，排除“未分类”）中选择最匹配的分类并写回
 * {@code asset_info.category_id}。
 * <p>
 * 采用主键游标分批处理，避免单次 Prompt 过大；由前端循环调用直至 hasMore=false。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetClassifyAiService {

    /** 单批默认处理的资产数量 */
    private static final int DEFAULT_BATCH_SIZE = 40;

    /** 单批允许的最大资产数量 */
    private static final int MAX_BATCH_SIZE = 80;

    /** “未分类”分类编码（补分类时的筛选条件） */
    private static final String UNCATEGORIZED_CODE = "UNCATEGORIZED";

    /** 每张表参与 Prompt 的字段数量上限 */
    private static final int MAX_COLUMNS_PER_TABLE = 15;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** agent HTTP 客户端（线程安全，类级单例复用） */
    private static final HttpClient AGENT_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final DamAssetMapper assetMapper;
    private final AssetCategoryRelMapper assetCategoryRelMapper;
    private final DamCategoryMapper categoryMapper;
    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;

    /** dam-agent 服务地址 */
    @Value("${dam.agent-url:http://localhost:8089}")
    private String agentUrl;

    /**
     * 对数据源下的表资产执行 AI 分类识别
     *
     * @param dataSourceId 数据源ID（为空则处理全部）
     * @param pendingOnly  true 时只处理“未分类”的资产
     * @param afterId      游标：上一批最后一个资产ID
     * @param batchSize    本批处理资产数（默认 40，上限 80）
     * @param token        当前登录 token（透传给 dam-agent 鉴权）
     * @return 统计结果 {total, processed, classified, remaining, nextCursor, hasMore, batchSize}
     */
    @Transactional
    public Map<String, Object> classify(Long dataSourceId, boolean pendingOnly, Long afterId,
                                        Integer batchSize, String token) {
        int limit = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : Math.min(batchSize, MAX_BATCH_SIZE);
        Long cursor = afterId == null || afterId <= 0 ? null : afterId;

        // 1. 可用于归类的分类（排除“未分类”）
        Map<String, DamCategory> targetCategories = loadTargetCategories();
        if (targetCategories.isEmpty()) {
            throw new BusinessException("暂无可用的资产分类，请先在资产分类中维护分类树");
        }

        // 2. 本批待分类资产（按主键升序游标翻页，保证每次调用向前推进）
        List<DamAsset> assets = assetMapper.selectList(
                filterQuery(dataSourceId, pendingOnly, cursor, true).last("LIMIT " + limit));
        if (assets.isEmpty()) {
            return batchResult(0, 0, 0, 0, null, false, limit);
        }

        long total = assetMapper.selectCount(filterQuery(dataSourceId, pendingOnly, null, false));

        // 3. 组装表结构上下文并调用智能体
        String context = buildContext(assets);
        String raw = callAgent(buildPrompt(context, targetCategories.values()), token);
        List<Map<String, Object>> items = parse(raw);
        if (items.isEmpty()) {
            log.warn("资产分类识别结果为空（将导致本批 classified=0），dataSourceId={}, 原始返回前300字={}",
                    dataSourceId, StrUtil.sub(raw, 0, 300));
        }

        // 4. 回写 category_id（同名表可能分属不同 Schema，按名称聚合回写）
        Map<String, List<DamAsset>> byName = new HashMap<>();
        for (DamAsset a : assets) {
            byName.computeIfAbsent(normName(a.getAssetName()), k -> new ArrayList<>()).add(a);
        }
        int classified = 0;
        int unmatched = 0;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (Map<String, Object> it : items) {
            DamCategory cat = matchCategory(targetCategories, str(it.get("categoryCode")), str(it.get("categoryName")));
            if (cat == null) {
                unmatched++;
                continue;
            }
            List<DamAsset> matched = byName.get(normName(str(it.get("tableName"))));
            if (matched == null || matched.isEmpty()) {
                matched = byName.get(normName(str(it.get("assetName"))));
            }
            if (matched == null || matched.isEmpty()) {
                unmatched++;
                continue;
            }
            for (DamAsset a : matched) {
                boolean changed = !Objects.equals(a.getCategoryId(), cat.getId());
                if (changed) {
                    a.setCategoryId(cat.getId());
                    a.setUpdatedAt(now);
                    a.setUpdatedBy("ai");
                    assetMapper.updateById(a);
                    classified++;
                }
                // 同步主题域归属（同一主题域只保留一个类目）
                upsertCategoryRel(a.getId(), cat, now);
            }
        }
        if (unmatched > 0) {
            log.info("资产分类结果未匹配到资产/类目 {} 条（本批共 {} 条，模型返回 {} 条）；模型首条={}，本批资产={}",
                    unmatched, assets.size(), items.size(),
                    items.isEmpty() ? "-" : items.get(0),
                    assets.stream().map(DamAsset::getAssetName).collect(Collectors.toList()));
        }

        // 5. 游标与剩余数量
        Long nextCursor = assets.getLast().getId();
        long remaining = assetMapper.selectCount(filterQuery(dataSourceId, pendingOnly, nextCursor, false));
        return batchResult(total, assets.size(), classified, remaining, nextCursor, remaining > 0, limit);
    }

    /**
     * 写入/更新资产在类目所属主题域下的归属（同域唯一）
     */
    private void upsertCategoryRel(Long assetId, DamCategory cat, java.time.LocalDateTime now) {
        if (assetId == null || cat == null || cat.getParentId() == null) return;
        AssetCategoryRel rel = assetCategoryRelMapper.selectOne(
                new LambdaQueryWrapper<AssetCategoryRel>()
                        .eq(AssetCategoryRel::getAssetId, assetId)
                        .eq(AssetCategoryRel::getSubjectDomainId, cat.getParentId())
                        .last("LIMIT 1"));
        if (rel == null) {
            rel = new AssetCategoryRel();
            rel.setAssetId(assetId);
            rel.setSubjectDomainId(cat.getParentId());
            rel.setCategoryId(cat.getId());
            rel.setCreatedAt(now);
            rel.setUpdatedAt(now);
            rel.setCreatedBy("ai");
            rel.setUpdatedBy("ai");
            assetCategoryRelMapper.insert(rel);
        } else if (!Objects.equals(rel.getCategoryId(), cat.getId())) {
            rel.setCategoryId(cat.getId());
            rel.setUpdatedAt(now);
            rel.setUpdatedBy("ai");
            assetCategoryRelMapper.updateById(rel);
        }
    }

    /** 统一返回结构 */
    private Map<String, Object> batchResult(long total, int processed, int classified, long remaining,
                                            Long nextCursor, boolean hasMore, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("processed", processed);
        result.put("classified", classified);
        result.put("remaining", remaining);
        result.put("nextCursor", nextCursor);
        result.put("hasMore", hasMore);
        result.put("batchSize", limit);
        return result;
    }

    /**
     * 构建查询条件
     *
     * @param pendingOnly true 时只取分类为“未分类”的资产
     * @param afterId     主键游标
     * @param order       是否按主键升序（统计时不能带 ORDER BY）
     */
    private LambdaQueryWrapper<DamAsset> filterQuery(Long dataSourceId, boolean pendingOnly,
                                                     Long afterId, boolean order) {
        LambdaQueryWrapper<DamAsset> w = new LambdaQueryWrapper<>();
        w.eq(DamAsset::getSourceMetadataType, "TABLE");
        w.eq(dataSourceId != null, DamAsset::getDataSourceId, dataSourceId);
        w.gt(afterId != null, DamAsset::getId, afterId);
        if (pendingOnly) {
            Long uncategorizedId = resolveUncategorizedId();
            if (uncategorizedId != null) {
                w.eq(DamAsset::getCategoryId, uncategorizedId);
            }
        }
        if (order) w.orderByAsc(DamAsset::getId);
        return w;
    }

    /** “未分类”分类ID（不存在返回 null，此时不做额外过滤） */
    private Long resolveUncategorizedId() {
        DamCategory def = categoryMapper.selectOne(
                new LambdaQueryWrapper<DamCategory>()
                        .eq(DamCategory::getCategoryCode, UNCATEGORIZED_CODE)
                        .last("LIMIT 1"));
        return def != null ? def.getId() : null;
    }

    /** 可归类目标：启用分类中的二级类目（排除“未分类”与一级主题域），key 为归一化编码与名称 */
    private Map<String, DamCategory> loadTargetCategories() {
        List<DamCategory> list = categoryMapper.selectList(
                new LambdaQueryWrapper<DamCategory>()
                        .eq(DamCategory::getStatus, 1)
                        .eq(DamCategory::getTreeLevel, 2L)
                        .isNotNull(DamCategory::getParentId));
        Map<String, DamCategory> map = new LinkedHashMap<>();
        for (DamCategory c : list) {
            if (UNCATEGORIZED_CODE.equalsIgnoreCase(c.getCategoryCode())) continue;
            if (StrUtil.isNotBlank(c.getCategoryCode())) {
                map.put(normKey(c.getCategoryCode()), c);
            }
            if (StrUtil.isNotBlank(c.getCategoryName())) {
                map.putIfAbsent(normKey(c.getCategoryName()), c);
            }
        }
        return map;
    }

    /** 按编码或名称匹配分类（兼容模型输出“MANAGE（管理数据）”这类带说明的写法） */
    private DamCategory matchCategory(Map<String, DamCategory> targetCategories, String code, String name) {
        for (String candidate : new String[]{code, name}) {
            if (StrUtil.isBlank(candidate)) continue;
            String k = normKey(candidate);
            if (k.isEmpty()) continue;
            DamCategory exact = targetCategories.get(k);
            if (exact != null) return exact;
            // 包含匹配：取最长的命中键，避免短键误命中
            DamCategory best = null;
            int bestLen = 0;
            for (Map.Entry<String, DamCategory> e : targetCategories.entrySet()) {
                String key = e.getKey();
                if (key.isEmpty() || key.length() <= bestLen) continue;
                if (k.contains(key)) {
                    best = e.getValue();
                    bestLen = key.length();
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** 构建表结构 + 归类线索上下文 */
    private String buildContext(List<DamAsset> assets) {
        Set<Long> tableIds = assets.stream().map(DamAsset::getSourceMetadataId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MetadataTable> tableMap = new HashMap<>();
        if (!tableIds.isEmpty()) {
            for (MetadataTable t : tableMapper.selectByIds(tableIds)) {
                if (t != null) tableMap.put(t.getId(), t);
            }
        }
        Map<Long, List<MetadataColumn>> colsMap = new HashMap<>();
        if (!tableIds.isEmpty()) {
            colsMap = columnMapper.selectList(
                            new LambdaQueryWrapper<MetadataColumn>()
                                    .in(MetadataColumn::getTableId, tableIds)
                                    .orderByAsc(MetadataColumn::getOrdinalPosition))
                    .stream().collect(Collectors.groupingBy(MetadataColumn::getTableId));
        }

        StringBuilder sb = new StringBuilder();
        for (DamAsset a : assets) {
            MetadataTable mt = a.getSourceMetadataId() != null ? tableMap.get(a.getSourceMetadataId()) : null;
            String comment = mt != null && StrUtil.isNotBlank(mt.getTableComment())
                    ? mt.getTableComment() : a.getDescription();
            sb.append("=== ").append(a.getAssetName());
            if (StrUtil.isNotBlank(comment)) sb.append("（").append(comment).append("）");
            sb.append(" ===\n");
            if (mt != null && StrUtil.isNotBlank(mt.getSubjectDomain())) {
                sb.append("主题域: ").append(mt.getSubjectDomain()).append("\n");
            }
            List<MetadataColumn> cols = mt == null ? Collections.emptyList()
                    : colsMap.getOrDefault(mt.getId(), Collections.emptyList());
            if (!cols.isEmpty()) {
                sb.append("字段: ");
                sb.append(cols.stream().limit(MAX_COLUMNS_PER_TABLE)
                        .map(MetadataColumn::getColumnName).collect(Collectors.joining(", ")));
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 组装分类识别 Prompt */
    private String buildPrompt(String context, Collection<DamCategory> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是医疗行业数据治理专家。请根据下列数据库表的结构与注释，为每张表选择最匹配的资产分类。\n");
        sb.append("可选分类（只能从中选择，必须原样输出 categoryCode）：\n");
        for (DamCategory c : categories) {
            sb.append("- ").append(c.getCategoryCode()).append("（").append(c.getCategoryName()).append("）");
            if (StrUtil.isNotBlank(c.getDescription())) sb.append("：").append(c.getDescription());
            sb.append("\n");
        }
        sb.append("\n判断原则：以表名、表注释、主题域与字段语义为准；只能从上面列出的二级类目中选择，不要选择一级主题域，也不要编造新类目。\n");
        sb.append("categoryCode 只能填写上面的英文编码（如 HOSPITAL），不要附加中文名称、括号或任何解释。\n");
        sb.append("\n表信息：\n").append(context);
        sb.append("必须为上面列出的每一张表都输出一条记录，不得遗漏。\n");
        sb.append("只输出 JSON：{\"tables\":[{\"tableName\":\"真实表名\",\"categoryCode\":\"HOSPITAL\"}]}\n");
        sb.append("表名必须严格使用上面给出的真实名称，不要编造。");
        return sb.toString();
    }

    /** 调用 dam-agent 编码智能体，返回其 content */
    private String callAgent(String prompt, String token) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agentType", "code");
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            String json = MAPPER.writeValueAsString(body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl + "/api/agent/complete"))
                    .timeout(Duration.ofSeconds(150))
                    .header("Content-Type", "application/json");
            if (StrUtil.isNotBlank(token)) builder.header("token", token);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();

            HttpResponse<String> resp = AGENT_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BusinessException("智能体调用失败: HTTP " + resp.statusCode());
            }
            JsonNode node = MAPPER.readTree(resp.body());
            if (node.path("code").asInt(-1) != 200) {
                throw new BusinessException("智能体调用失败: " + node.path("message").asText("未知错误"));
            }
            return node.path("data").path("content").asText("");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用智能体进行资产分类识别失败", e);
            throw new BusinessException("智能体调用失败: " + e.getMessage());
        }
    }

    /** 解析 AI 输出的结构化 JSON */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parse(String content) {
        if (StrUtil.isBlank(content)) return Collections.emptyList();
        String text = content.trim()
                .replaceAll("(?s)^```[a-zA-Z]*\\s*", "")
                .replaceAll("(?s)```$", "").trim();
        try {
            JsonNode node = MAPPER.readTree(text);
            JsonNode arr = node.path("tables");
            if (arr.isArray()) return MAPPER.convertValue(arr, List.class);
        } catch (Exception e) {
            log.warn("资产分类识别结果解析失败: {}, 原始返回前300字={}", e.getMessage(), StrUtil.sub(text, 0, 300));
        }
        return Collections.emptyList();
    }

    /** 名称归一化：去引号/空白、统一小写，AI 若返回 schema.table 只取表名部分 */
    private String normName(String name) {
        if (StrUtil.isBlank(name)) return "";
        String n = name.trim().replace("\"", "").replace("`", "").replace("'", "");
        int dot = n.lastIndexOf('.');
        if (dot >= 0 && dot < n.length() - 1) n = n.substring(dot + 1);
        return n.trim().toLowerCase();
    }

    /** 编码/名称归一化键（去空格与分隔符，统一小写） */
    private String normKey(String key) {
        if (StrUtil.isBlank(key)) return "";
        return key.trim().replaceAll("[\\s_\\-]", "").toLowerCase();
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }
}
