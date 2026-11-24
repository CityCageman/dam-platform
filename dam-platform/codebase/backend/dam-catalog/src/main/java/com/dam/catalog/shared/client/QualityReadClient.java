package com.dam.catalog.shared.client;

import cn.dev33.satoken.context.SaHolder;
import com.dam.platform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** catalog 读取质量摘要，质量表由 dam-quality 独占。 */
@Component
public class QualityReadClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${dam.quality-url:http://127.0.0.1:8085}")
    private String qualityUrl;

    public Map<String, Object> assetSummary(Long tableId) {
        String raw = RestClient.builder().baseUrl(qualityUrl).build()
                .get().uri("/api/quality/assets/{id}/summary", tableId)
                .header("token", token()).retrieve().body(String.class);
        try {
            JsonNode root = MAPPER.readTree(raw == null ? "{}" : raw);
            if (root.path("code").asInt(200) != 200) {
                throw new BusinessException(root.path("message").asText("质量摘要查询失败"));
            }
            JsonNode data = root.path("data");
            return MAPPER.convertValue(data, Map.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("质量摘要响应解析失败: " + e.getMessage());
        }
    }

    private String token() {
        try { return SaHolder.getRequest().getHeader("token"); }
        catch (Exception e) { return ""; }
    }
}
