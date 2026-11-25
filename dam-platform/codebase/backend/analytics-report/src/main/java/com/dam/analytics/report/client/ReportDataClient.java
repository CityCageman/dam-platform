package com.dam.analytics.report.client;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** 通过 DAM 报表数据接口读取数据源和资产注释。 */
@Component
public class ReportDataClient {
    private final RestClient client;

    public ReportDataClient(@Value("${dam.asset-catalog-url:http://127.0.0.1:8092}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Map<String, Object> dataSource(Long id) {
        Map<?, ?> body = client.get().uri("/api/catalog/report-data/data-source/{id}", id)
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        if (!(data instanceof Map<?, ?> m)) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    public Map<String, Object> assetComments(Long dataSourceId) {
        Map<?, ?> body = client.get().uri("/api/catalog/report-data/asset-comments?dataSourceId=" + dataSourceId)
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        if (!(data instanceof Map<?, ?> m)) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private String token() {
        try { return StpUtil.getTokenValue(); } catch (Exception e) { return ""; }
    }
}
