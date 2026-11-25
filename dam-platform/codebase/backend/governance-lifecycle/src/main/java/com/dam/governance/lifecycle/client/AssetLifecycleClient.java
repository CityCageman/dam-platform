package com.dam.governance.lifecycle.client;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过 DAM 资产 API 和运维统计 API 读取/归档资产。 */
@Component
public class AssetLifecycleClient {
    private final RestClient asset;
    private final RestClient operations;

    public AssetLifecycleClient(
            @Value("${dam.asset-catalog-url:http://127.0.0.1:8092}") String assetUrl,
            @Value("${dam.operations-url:http://127.0.0.1:8088}") String operationsUrl) {
        this.asset = RestClient.builder().baseUrl(assetUrl).build();
        this.operations = RestClient.builder().baseUrl(operationsUrl).build();
    }

    public List<Map<String, Object>> assets() {
        Map<?, ?> body = asset.get().uri("/api/catalog/assets/search?page=1&pageSize=1000")
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        Object records = data instanceof Map<?, ?> m ? m.get("records") : null;
        List<Map<String, Object>> out = new ArrayList<>();
        if (records instanceof List<?> list) for (Object row : list) if (row instanceof Map<?, ?> m) out.add(copy(m));
        return out;
    }

    public void updateStatus(Long id, int status) {
        asset.put().uri("/api/catalog/assets")
                .header("token", token())
                .body(Map.of("id", id, "status", status))
                .retrieve().body(Map.class);
    }

    public Map<String, Object> latestStats(Long assetId) {
        Map<?, ?> body = operations.get().uri("/api/operations/usage-stats")
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        if (!(data instanceof List<?> list)) return null;
        Map<String, Object> best = null;
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> m)) continue;
            Object id = m.get("assetId");
            if (id instanceof Number n && assetId != null && n.longValue() == assetId) {
                best = copy(m);
                break;
            }
        }
        return best;
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
