package com.dam.aiworkbench.service;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调用 catalog 的领域工具接口。
 * AI 工作台不直接访问 catalog 的元数据/报表表。
 */
@Component
@RequiredArgsConstructor
public class CatalogToolClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${dam.ai-workbench.catalog-url:http://127.0.0.1:8092}")
    private String catalogUrl;

    private RestClient client() {
        return RestClient.builder().baseUrl(catalogUrl).build();
    }

    public Map<String, Object> annotate(Map<String, Object> body) {
        return post("/api/catalog/internal/ai/annotate", body);
    }

    public Map<String, Object> classify(Map<String, Object> body) {
        return post("/api/catalog/internal/ai/classify", body);
    }

    public void applyAnnotation(Map<String, Object> body) {
        post("/api/catalog/internal/ai/apply-annotation", body);
    }

    public void applyClassification(Map<String, Object> body) {
        post("/api/catalog/internal/ai/apply-classification", body);
    }

    public Map<String, Object> generateReport(Map<String, Object> body) {
        return post("/api/catalog/internal/ai/reports/generate", body);
    }

    public String generateTitle(String requirement, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requirement", requirement);
        body.put("token", token);
        Object data = post("/api/catalog/internal/ai/reports/title", body).get("value");
        return data == null ? null : String.valueOf(data);
    }

    public Map<String, Object> getReport(Long id) {
        return get("/api/catalog/internal/ai/reports/" + id);
    }

    public Map<String, Object> saveGenerated(Map<String, Object> body, String operator) {
        body.put("operator", operator);
        return post("/api/catalog/internal/ai/reports/save-generated", body);
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        Object bodyToken = body.get("token");
        String token = bodyToken == null || String.valueOf(bodyToken).isBlank()
                ? currentToken() : String.valueOf(bodyToken);
        if (String.valueOf(token).isBlank()) token = currentToken();
        body.putIfAbsent("token", token);
        String raw = client().post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("token", token)
                .body(body)
                .retrieve()
                .body(String.class);
        return unwrap(raw);
    }

    private Map<String, Object> get(String path) {
        String raw = client().get().uri(path)
                .header("token", currentToken())
                .retrieve()
                .body(String.class);
        return unwrap(raw);
    }

    private Map<String, Object> unwrap(String raw) {
        try {
            JsonNode root = MAPPER.readTree(raw == null ? "{}" : raw);
            if (root.path("code").asInt(200) != 200) {
                throw new BusinessException(root.path("message").asText("catalog 工具调用失败"));
            }
            JsonNode data = root.path("data");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("value", data.isMissingNode() || data.isNull() ? null : MAPPER.convertValue(data, Object.class));
            if (data.isObject()) {
                data.properties().forEach(e -> result.put(e.getKey(), MAPPER.convertValue(e.getValue(), Object.class)));
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("catalog 工具响应解析失败: " + e.getMessage());
        }
    }

    private String currentToken() {
        try {
            return StpUtil.getTokenValue();
        } catch (Exception e) {
            return "";
        }
    }
}
