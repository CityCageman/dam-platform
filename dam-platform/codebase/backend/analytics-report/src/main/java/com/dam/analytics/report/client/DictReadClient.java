package com.dam.analytics.report.client;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过 DAM 字典 API 读取字典，不直接依赖平台系统服务。 */
@Component
public class DictReadClient {
    private final RestClient client;

    public DictReadClient(@Value("${dam.asset-catalog-url:http://127.0.0.1:8092}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<Map<String, Object>> byType(String typeCode) {
        Map<?, ?> body = client.get().uri("/api/catalog/dict/{typeCode}", typeCode)
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        List<Map<String, Object>> out = new ArrayList<>();
        if (data instanceof List<?> list) {
            for (Object row : list) if (row instanceof Map<?, ?> m) {
                Map<String, Object> item = new LinkedHashMap<>();
                m.forEach((k, v) -> item.put(String.valueOf(k), v));
                out.add(item);
            }
        }
        return out;
    }

    private String token() {
        try { return StpUtil.getTokenValue(); } catch (Exception e) { return ""; }
    }
}
