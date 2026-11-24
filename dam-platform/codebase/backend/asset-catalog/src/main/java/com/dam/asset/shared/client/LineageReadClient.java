package com.dam.asset.shared.client;

import cn.dev33.satoken.context.SaHolder;
import com.dam.platform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** catalog 读取血缘，血缘表由 governance-lineage 独占。 */
@Component
public class LineageReadClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${dam.lineage-url:http://127.0.0.1:8093}")
    private String lineageUrl;

    public List<Map<String, Object>> byAsset(Long assetId) {
        String raw = RestClient.builder().baseUrl(lineageUrl).build()
                .get().uri("/api/lineage/{id}", assetId)
                .header("token", token()).retrieve().body(String.class);
        try {
            JsonNode root = MAPPER.readTree(raw == null ? "{}" : raw);
            if (root.path("code").asInt(200) != 200) {
                throw new BusinessException(root.path("message").asText("血缘查询失败"));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode node : root.path("data")) {
                rows.add(MAPPER.convertValue(node, Map.class));
            }
            return rows;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("血缘响应解析失败: " + e.getMessage());
        }
    }

    private String token() {
        try { return SaHolder.getRequest().getHeader("token"); }
        catch (Exception e) { return ""; }
    }
}
