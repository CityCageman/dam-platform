package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.entity.MetadataColumn;
import com.dam.catalog.entity.MetadataManagement;
import com.dam.catalog.entity.MetadataTable;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.catalog.repository.MetadataColumnMapper;
import com.dam.catalog.repository.MetadataManagementMapper;
import com.dam.catalog.repository.MetadataTableMapper;
import com.dam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 元数据 AI 智能标注服务
 * <p>
 * 同步表后，调用 dam-agent 编码智能体，为表自动生成：
 * <ul>
 *   <li>中文表注释（table_comment）</li>
 *   <li>业务主题域分类（subject_domain）</li>
 *   <li>数据安全分级（metadata_management.security_classification）</li>
 * </ul>
 * 由配置项 {@code dam.agent-url} 指定 dam-agent 服务地址。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataAiService {

    /** 单批默认处理的表数量（避免 Prompt 过大导致智能体超时/漏标） */
    private static final int DEFAULT_BATCH_SIZE = 40;

    /** 单批允许的最大表数量 */
    private static final int MAX_BATCH_SIZE = 80;

    /** metadata_management 表全限定名（安全分级缺失判断用） */
    private static final String MGMT_TABLE = "dam_metadata.metadata_management";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** agent HTTP 客户端（线程安全，类级单例复用） */
    private static final HttpClient AGENT_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final MetadataTableMapper tableMapper;
    private final MetadataColumnMapper columnMapper;
    private final MetadataManagementMapper managementMapper;
    private final DamAssetMapper assetMapper;

    /** asset_info.description 列长度上限（超长截断，避免写库报错） */
    private static final int ASSET_DESC_MAX_LEN = 500;

    /** dam-agent 服务地址 */
    @Value("${dam.agent-url:http://localhost:8089}")
    private String agentUrl;

    /**
     * 对数据源下的表执行 AI 自动标注（表注释 / 主题域 / 安全分级）
     *
     * @param dataSourceId 数据源ID（为空则标注全部）
     * @param pendingOnly  仅标注缺注释/缺主题域/缺安全分级的表（补标注）
     * @param afterId      游标：上一批最后一张表的ID，null 或 0 表示从头开始
     * @param batchSize    本批处理表数（默认 40，上限 80）
     * @param token        当前登录 token（透传给 dam-agent 鉴权）
     * @return 统计结果 {total, processed, annotated, domainAnnotated, levelAnnotated, nextCursor, hasMore, remaining}
     */
    @Transactional
    public Map<String, Object> annotate(Long dataSourceId, boolean pendingOnly, Long afterId, Integer batchSize, String token) {
        int limit = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : Math.min(batchSize, MAX_BATCH_SIZE);
        Long cursor = afterId == null || afterId <= 0 ? null : afterId;

        // 1. 本批待标注的表：按主键升序游标翻页，保证每次调用都向前推进（不会重复标注同一批）
        List<MetadataTable> tables = tableMapper.selectList(
                filterQuery(dataSourceId, pendingOnly, cursor, true).last("LIMIT " + limit));
        if (tables.isEmpty()) {
            // 无待处理表：返回空批次，由前端提示「无可标注/无需补标注」
            return batchResult(0, 0, 0, 0, 0, null, false, 0, limit);
        }

        // 2. 符合条件的表总数（用于前端展示进度）
        long total = tableMapper.selectCount(filterQuery(dataSourceId, pendingOnly, null, false));

        // 3. 组装表结构 + 字段上下文，调用编码智能体
        String context = buildContext(tables);
        String raw = callAgent(buildPrompt(context), token);
        List<Map<String, Object>> items = parse(raw);

        // 4. 回写：表注释 / 主题域 / 安全分级（同名表可能分属不同 Schema，按名称聚合回写）
        Map<String, List<MetadataTable>> byName = new HashMap<>();
        for (MetadataTable t : tables) {
            byName.computeIfAbsent(normName(t.getTableName()), k -> new ArrayList<>()).add(t);
        }

        int annotated = 0, domainAnnotated = 0, levelAnnotated = 0;
        // 表ID → AI 生成的中文注释（用于同步写回资产描述）
        Map<Long, String> commentByTable = new LinkedHashMap<>();
        for (Map<String, Object> it : items) {
            List<MetadataTable> matched = byName.get(normName(str(it.get("tableName"))));
            if (matched == null || matched.isEmpty()) continue;

            String comment = str(it.get("tableComment"));
            String domain = str(it.get("subjectDomain"));
            String level = str(it.get("securityLevel"));
            if (StrUtil.isBlank(comment) && StrUtil.isBlank(domain) && StrUtil.isBlank(level)) continue;

            for (MetadataTable t : matched) {
                if (StrUtil.isNotBlank(comment)) {
                    t.setTableComment(comment);
                    annotated++;
                    commentByTable.put(t.getId(), comment);
                }
                if (StrUtil.isNotBlank(domain)) {
                    t.setSubjectDomain(domain);
                    domainAnnotated++;
                }
                if (StrUtil.isNotBlank(comment) || StrUtil.isNotBlank(domain)) {
                    t.setUpdatedAt(java.time.LocalDateTime.now());
                    t.setUpdatedBy("ai");
                    tableMapper.updateById(t);
                }
                if (StrUtil.isNotBlank(level)) {
                    upsertSecurity(t.getId(), level);
                    levelAnnotated++;
                }
            }
        }

        // 4.1 表注释同步到资产描述（前端资产/表列表展示的是 asset_info.description）
        syncAssetDescriptions(commentByTable);

        // 5. 计算游标与剩余数量，供前端续标
        Long nextCursor = tables.getLast().getId();
        long remaining = tableMapper.selectCount(filterQuery(dataSourceId, pendingOnly, nextCursor, false));
        return batchResult(total, tables.size(), annotated, domainAnnotated, levelAnnotated,
                nextCursor, remaining > 0, remaining, limit);
    }

    /** 统一返回结构 */
    private Map<String, Object> batchResult(long total, int processed, int annotated, int domainAnnotated,
                                            int levelAnnotated, Long nextCursor, boolean hasMore,
                                            long remaining, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("processed", processed);
        result.put("annotated", annotated);
        result.put("domainAnnotated", domainAnnotated);
        result.put("levelAnnotated", levelAnnotated);
        result.put("nextCursor", nextCursor);
        result.put("hasMore", hasMore);
        result.put("remaining", remaining);
        result.put("batchSize", limit);
        return result;
    }

    /**
     * 构建查询条件
     *
     * @param pendingOnly true 时只保留「缺表注释 / 缺主题域 / 缺安全分级」的表（补标注）
     * @param afterId     主键游标，取 id 大于该值的表
     * @param order       是否按主键升序（统计总数时不能带 ORDER BY）
     */
    private LambdaQueryWrapper<MetadataTable> filterQuery(Long dataSourceId, boolean pendingOnly,
                                                          Long afterId, boolean order) {
        LambdaQueryWrapper<MetadataTable> w = new LambdaQueryWrapper<>();
        w.eq(dataSourceId != null, MetadataTable::getDatasourceId, dataSourceId);
        w.gt(afterId != null, MetadataTable::getId, afterId);
        if (pendingOnly) {
            // 安全分级存放在 metadata_management，需 NOT EXISTS 子查询判断
            String notAnnotated = "NOT EXISTS (SELECT 1 FROM " + MGMT_TABLE
                    + " mm WHERE mm.table_id = metadata_table.id"
                    + " AND mm.security_classification IS NOT NULL AND mm.security_classification <> '')";
            w.and(q -> q.isNull(MetadataTable::getTableComment)
                    .or().eq(MetadataTable::getTableComment, "")
                    .or().isNull(MetadataTable::getSubjectDomain)
                    .or().eq(MetadataTable::getSubjectDomain, "")
                    .or().apply(notAnnotated));
        }
        if (order) w.orderByAsc(MetadataTable::getId);
        return w;
    }

    /** 表名归一化：去引号/空白、统一小写，AI 若返回 schema.table 只取表名部分 */
    private String normName(String name) {
        if (StrUtil.isBlank(name)) return "";
        String n = name.trim().replace("\"", "").replace("`", "").replace("'", "");
        int dot = n.lastIndexOf('.');
        if (dot >= 0 && dot < n.length() - 1) n = n.substring(dot + 1);
        return n.trim().toLowerCase();
    }

    /**
     * 将 AI 生成的中文表注释同步写入资产描述（asset_info.description）
     * <p>
     * 资产与技术元数据通过 source_metadata_type/source_metadata_id 多态外键关联，
     * 元数据页/资产页的表注释均读取 asset_info.description，不同步会导致
     * “AI 已标注但页面表注释仍为空”。
     *
     * @param commentByTable metadata_table.id → 中文表注释
     */
    private void syncAssetDescriptions(Map<Long, String> commentByTable) {
        if (commentByTable.isEmpty()) return;
        List<DamAsset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<DamAsset>()
                        .in(DamAsset::getSourceMetadataId, commentByTable.keySet()));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (DamAsset a : assets) {
            String comment = commentByTable.get(a.getSourceMetadataId());
            if (StrUtil.isBlank(comment) || comment.equals(a.getDescription())) continue;
            a.setDescription(comment.length() > ASSET_DESC_MAX_LEN
                    ? comment.substring(0, ASSET_DESC_MAX_LEN) : comment);
            a.setUpdatedAt(now);
            a.setUpdatedBy("ai");
            assetMapper.updateById(a);
        }
    }

    /** 写入/更新表的安全分级（metadata_management，按 table_id 幂等） */
    private void upsertSecurity(Long tableId, String level) {
        MetadataManagement mgmt = managementMapper.selectOne(
                new LambdaQueryWrapper<MetadataManagement>()
                        .eq(MetadataManagement::getTableId, tableId)
                        .last("LIMIT 1"));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (mgmt != null) {
            mgmt.setSecurityClassification(level);
            mgmt.setUpdatedAt(now);
            mgmt.setUpdatedBy("ai");
            managementMapper.updateById(mgmt);
        } else {
            MetadataManagement m = getMetadataManagement(tableId, level, now);
            managementMapper.insert(m);
        }
    }

    private static @NonNull MetadataManagement getMetadataManagement(Long tableId, String level, LocalDateTime now) {
        MetadataManagement m = new MetadataManagement();
        m.setTableId(tableId);
        m.setSecurityClassification(level);
        // governance_status_id / source_system_id 均为 NOT NULL 列，插入时必须赋值
        // 治理状态默认“待治理”（与字典 governance_status 一致）；来源系统表无外键约束，用 0 占位
        m.setGovernanceStatusId("TO_BE_GOVERNED");
        m.setSourceSystemId(0L);
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        m.setCreatedBy("ai");
        m.setUpdatedBy("ai");
        return m;
    }

    /** 构建表结构 + 字段上下文 */
    private String buildContext(List<MetadataTable> tables) {
        Set<Long> tableIds = tables.stream().map(MetadataTable::getId).collect(Collectors.toSet());
        Map<Long, List<MetadataColumn>> colsMap = columnMapper.selectList(
                        new LambdaQueryWrapper<MetadataColumn>()
                                .in(MetadataColumn::getTableId, tableIds)
                                .orderByAsc(MetadataColumn::getOrdinalPosition))
                .stream().collect(Collectors.groupingBy(MetadataColumn::getTableId));

        StringBuilder sb = new StringBuilder();
        for (MetadataTable t : tables) {
            sb.append("=== ").append(t.getTableName());
            if (StrUtil.isNotBlank(t.getTableComment())) sb.append("（").append(t.getTableComment()).append("）");
            sb.append(" ===\n");
            for (MetadataColumn c : colsMap.getOrDefault(t.getId(), Collections.emptyList())) {
                sb.append("- ").append(c.getColumnName())
                        .append(" (").append(StrUtil.isBlank(c.getColumnType()) ? "未知类型" : c.getColumnType()).append(")");
                if (StrUtil.isNotBlank(c.getColumnComment())) sb.append(" # ").append(c.getColumnComment());
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 组装 AI 标注 Prompt */
    private String buildPrompt(String context) {
        return "你是医疗行业数据治理专家。请根据下列表结构与字段信息，为每张表给出：\n"
                + "1. tableComment：简洁中文业务注释（不超过30字）\n"
                + "2. subjectDomain：业务主题域，只能从以下枚举取值："
                + "PATIENT(患者域)、CLINICAL(临床域)、TREATMENT(诊疗域)、SURGERY(手术域)、MEDICATION(药品域)、"
                + "FINANCE(费用域)、ADMIN(管理域)、REFERENCE(基础数据域)、LOG(日志域)、WORKFLOW(流程域)\n"
                + "3. securityLevel：数据安全分级，只能取：public(公开)、internal(内部一般)、confidential(内部敏感)、"
                + "restricted(重要数据/个人信息)、critical(核心数据)\n\n"
                + "表结构与字段：\n" + context + "\n"
                + "必须为上面列出的每一张表都输出一条记录，不得遗漏、不得合并，也不要编造不存在的表。\n"
                + "只输出 JSON：{\"tables\":[{\"tableName\":\"真实表名\",\"tableComment\":\"中文注释\",\"subjectDomain\":\"枚举值\",\"securityLevel\":\"枚举值\"}]}\n"
                + "表名必须严格使用上面给出的真实名称，不要编造。";
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
            log.error("调用智能体进行元数据标注失败", e);
            throw new BusinessException("智能体调用失败: " + e.getMessage());
        }
    }

    /** 解析 AI 输出的结构化标注 JSON */
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
            log.warn("AI 标注结果解析失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }
}
