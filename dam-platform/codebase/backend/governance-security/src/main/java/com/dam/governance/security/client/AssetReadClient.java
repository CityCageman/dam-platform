package com.dam.governance.security.client;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过 DAM 资产 API 读取资产和字段，不直接访问 dam_dam/metadata_column。 */
@Component
public class AssetReadClient {
    private final RestClient client;

    public AssetReadClient(@Value("${dam.asset-catalog-url:http://127.0.0.1:8092}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<Map<String, Object>> list() {
        Map<?, ?> body = client.get().uri("/api/catalog/assets/search?page=1&pageSize=1000")
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        Object records = data instanceof Map<?, ?> m ? m.get("records") : null;
        List<Map<String, Object>> out = new ArrayList<>();
        if (records instanceof List<?> list) for (Object row : list) if (row instanceof Map<?, ?> m) out.add(copy(m));
        return out;
    }

    public Map<String, Object> detail(Long id) {
        Map<?, ?> body = client.get().uri("/api/catalog/assets/{id}", id)
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        return data instanceof Map<?, ?> m ? copy(m) : null;
    }

    public List<Map<String, Object>> fields(Long assetId) {
        Map<String, Object> data = detail(assetId);
        Object fields = data == null ? null : data.get("fields");
        List<Map<String, Object>> out = new ArrayList<>();
        if (fields instanceof List<?> list) for (Object row : list) if (row instanceof Map<?, ?> m) out.add(copy(m));
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
