package com.dam.lineage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.lineage.entity.MetadataLineageSource;
import com.dam.lineage.repository.LineageSearchMapper;
import com.dam.lineage.repository.MetadataLineageSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 血缘中文检索服务
 * <p>
 * 业务场景：用户在血缘页输入中文（如「医嘱号」），需要知道它出现在哪些表/字段、
 * 又流向了哪些报表与下游表。实现分三步：
 * <ol>
 *   <li>按中文注释检索 metadata_column / metadata_table；</li>
 *   <li>用表名找到血缘节点（metadata_lineage_source.source_code）；</li>
 *   <li>复用 {@link LineageService#getLineageGraph} 拉取上下游，提取相关报表与数据表。</li>
 * </ol>
 * 查询失败时返回空结果而不是抛异常，避免影响页面其他功能。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineageSearchService {

    /** 单类命中最大返回条数 */
    private static final int MAX_LIMIT = 50;

    /** 最多做血缘展开的表数量（避免一次检索触发过多递归查询） */
    private static final int MAX_EXPAND_TABLES = 10;

    /** 血缘展开深度 */
    private static final int EXPAND_DEPTH = 6;

    /** 相关节点最多展示数量 */
    private static final int MAX_RELATED = 12;

    private final LineageSearchMapper searchMapper;
    private final MetadataLineageSourceMapper sourceMapper;
    private final LineageService lineageService;

    /**
     * 按中文关键字检索血缘
     *
     * @param keyword 关键字（中文或英文，空则返回空结果）
     * @param limit   每类最大条数（1~50）
     * @return {keyword,total,columns,tables}
     */
    public Map<String, Object> search(String keyword, Integer limit) {
        String kw = keyword == null ? "" : keyword.trim();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keyword", kw);
        if (kw.isEmpty()) {
            result.put("total", 0);
            result.put("columns", List.of());
            result.put("tables", List.of());
            return result;
        }

        int lim = Math.clamp(limit == null ? 20 : limit, 1, MAX_LIMIT);
        List<Map<String, Object>> columns;
        List<Map<String, Object>> tables;
        try {
            columns = searchMapper.searchColumns(kw, lim);
            tables = searchMapper.searchTables(kw, lim);
        } catch (Exception e) {
            log.warn("血缘中文检索失败 [keyword={}]: {}", kw, e.getMessage());
            result.put("total", 0);
            result.put("columns", List.of());
            result.put("tables", List.of());
            return result;
        }

        // 同一张表只展开一次血缘
        Map<String, List<String>> reportCache = new LinkedHashMap<>();
        Map<String, List<String>> tableCache = new LinkedHashMap<>();
        Map<String, Long> nodeCache = new LinkedHashMap<>();
        int expanded = 0;
        for (Map<String, Object> col : columns) {
            String tableName = str(col.get("table_name"));
            if (expanded < MAX_EXPAND_TABLES && tableName != null && !reportCache.containsKey(tableName)) {
                expand(tableName, reportCache, tableCache, nodeCache);
                expanded++;
            }
            col.put("nodeId", nodeCache.get(tableName));
            col.put("relatedReports", reportCache.getOrDefault(tableName, List.of()));
            col.put("relatedTables", tableCache.getOrDefault(tableName, List.of()));
        }
        for (Map<String, Object> tab : tables) {
            String tableName = str(tab.get("table_name"));
            if (expanded < MAX_EXPAND_TABLES && tableName != null && !reportCache.containsKey(tableName)) {
                expand(tableName, reportCache, tableCache, nodeCache);
                expanded++;
            }
            tab.put("nodeId", nodeCache.get(tableName));
            tab.put("relatedReports", reportCache.getOrDefault(tableName, List.of()));
            tab.put("relatedTables", tableCache.getOrDefault(tableName, List.of()));
        }

        result.put("total", columns.size() + tables.size());
        result.put("columns", columns);
        result.put("tables", tables);
        return result;
    }

    /**
     * 展开单张表的上下游，提取相关报表与数据表
     *
     * @param tableName    表名（对应血缘节点 source_code）
     * @param reportCache  报表缓存
     * @param tableCache   数据表缓存
     */
    private void expand(String tableName, Map<String, List<String>> reportCache, Map<String, List<String>> tableCache,
                        Map<String, Long> nodeCache) {
        List<String> reports = new ArrayList<>();
        List<String> relatedTables = new ArrayList<>();
        try {
            MetadataLineageSource node = sourceMapper.selectOne(
                    new LambdaQueryWrapper<MetadataLineageSource>()
                            .eq(MetadataLineageSource::getSourceCode, tableName)
                            .last("limit 1"));
            if (node != null && node.getId() != null) {
                nodeCache.put(tableName, node.getId());
                Map<String, Object> graph = lineageService.getLineageGraph(node.getId(), EXPAND_DEPTH, false);
                Object nodesObj = graph.get("nodes");
                if (nodesObj instanceof List<?> nodes) {
                    Set<String> reportSet = new LinkedHashSet<>();
                    Set<String> tableSet = new LinkedHashSet<>();
                    for (Object o : nodes) {
                        if (!(o instanceof Map<?, ?> n)) continue;
                        String label = str(n.get("label"));
                        String type = str(n.get("type"));
                        if (label == null || label.isBlank()) continue;
                        if ("REPORT".equalsIgnoreCase(type)) {
                            reportSet.add(label);
                        } else if ("TABLE".equalsIgnoreCase(type) && !label.equals(tableName)) {
                            tableSet.add(label);
                        }
                    }
                    reports = reportSet.stream().limit(MAX_RELATED).toList();
                    relatedTables = tableSet.stream().limit(MAX_RELATED).toList();
                }
            }
        } catch (Exception e) {
            log.warn("血缘展开失败 [table={}]: {}", tableName, e.getMessage());
        }
        reportCache.put(tableName, reports);
        tableCache.put(tableName, relatedTables);
    }

    /**
     * 安全取字符串
     *
     * @param v 原值
     * @return 字符串或 null
     */
    private String str(Object v) {
        return v == null ? null : Objects.toString(v, null);
    }
}
