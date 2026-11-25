package com.dam.platform.operations.client;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过 DAM 资产 API 读取资产，不直接访问 dam_asset。 */
@Component
public class AssetReadClient {

    private final RestClient client;

    public AssetReadClient(@Value("${dam.asset-catalog-url:http://127.0.0.1:8092}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public long count() {
        Map<?, ?> body = client.get().uri("/api/catalog/assets/search?page=1&pageSize=1")
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        Object total = data instanceof Map<?, ?> m ? m.get("total") : null;
        return total instanceof Number n ? n.longValue() : 0L;
    }

    public List<Map<String, Object>> list(int size) {
        Map<?, ?> body = client.get().uri("/api/catalog/assets/search?page=1&pageSize=" + Math.max(1, Math.min(size, 1000)))
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        Object records = data instanceof Map<?, ?> m ? m.get("records") : null;
        List<Map<String, Object>> result = new ArrayList<>();
        if (records instanceof List<?> list) {
            for (Object row : list) if (row instanceof Map<?, ?> m) result.add(copy(m));
        }
        return result;
    }

    public Map<String, Object> detail(Long id) {
        Map<?, ?> body = client.get().uri("/api/catalog/assets/{id}", id)
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        if (data instanceof Map<?, ?> m && m.get("asset") instanceof Map<?, ?> asset) return copy(asset);
        return null;
    }

    public double averageQualityScore() {
        List<Map<String, Object>> assets = list(500);
        return assets.stream().map(a -> a.get("qualityScore")).filter(v -> v instanceof Number)
                .mapToDouble(v -> ((Number) v).doubleValue()).average().orElse(0D);
    }

    public Map<String, Long> monthlyAssetCounts(int months) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> a : list(1000)) {
            Object createdAt = a.get("createdAt");
            if (createdAt == null) continue;
            String s = String.valueOf(createdAt);
            if (s.length() >= 7) result.merge(s.substring(0, 7).replace('-', '.'), 1L, Long::sum);
        }
        return result;
    }

    public BigDecimal qualityScore(Map<String, Object> asset) {
        Object v = asset.get("qualityScore");
        return v instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : null;
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
