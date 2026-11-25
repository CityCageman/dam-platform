package com.dam.governance.lineage.client;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过 DAM 只读 API 获取血缘所需元数据，禁止直接查询 dam_dam。 */
@Component
public class LineageDataClient {
    private final RestClient client;

    public LineageDataClient(@Value("${dam.asset-catalog-url:http://127.0.0.1:8092}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<Map<String, Object>> tableLayers(List<String> names) {
        return list("/api/catalog/lineage-data/table-layers?names=" + String.join(",", names));
    }

    public List<Map<String, Object>> searchColumns(String keyword, int limit) {
        return list("/api/catalog/lineage-data/search-columns?keyword=" + keyword + "&limit=" + limit);
    }

    public List<Map<String, Object>> searchTables(String keyword, int limit) {
        return list("/api/catalog/lineage-data/search-tables?keyword=" + keyword + "&limit=" + limit);
    }

    public List<Map<String, Object>> sourceSystems(List<Long> ids) {
        String q = ids == null || ids.isEmpty() ? "" : "?ids=" + String.join(",", ids.stream().map(String::valueOf).toList());
        return list("/api/catalog/lineage-data/source-systems" + q);
    }

    public List<Map<String, Object>> nodeMeta(List<Long> ids) {
        return List.of();
    }

    private List<Map<String, Object>> list(String path) {
        Map<?, ?> body = client.get().uri(path).header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        List<Map<String, Object>> out = new ArrayList<>();
        if (data instanceof List<?> items) for (Object row : items) if (row instanceof Map<?, ?> m) out.add(copy(m));
        return out;
    }

    private Map<String, Object> copy(Map<?, ?> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        source.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private String token() {
        try { return StpUtil.getTokenValue(); } catch (Exception e) { return ""; }
    }
}
