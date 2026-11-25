package com.dam.governance.quality.client;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 通过 DAM API 查询质量规则目标表名。 */
@Component
public class QualityTableClient {
    private final RestClient client;

    public QualityTableClient(@Value("${dam.asset-catalog-url:http://127.0.0.1:8092}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public String findTableName(Long id) {
        if (id == null) return null;
        Map<?, ?> body = client.get().uri("/api/catalog/lineage-data/table-name/{id}", id)
                .header("token", token()).retrieve().body(Map.class);
        Object data = body == null ? null : body.get("data");
        return data == null ? null : String.valueOf(data);
    }

    private String token() {
        try { return StpUtil.getTokenValue(); } catch (Exception e) { return ""; }
    }
}
