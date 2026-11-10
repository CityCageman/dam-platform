package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import com.dam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;

/**
 * WeKnora 知识库 API 客户端。
 * 文档：https://github.com/Tencent/WeKnora/blob/main/website-docs/04-api/02-api-knowledge.md
 */
@Slf4j
@Service
public class WeKnoraClient {

    private final String baseUrl;
    private final String apiKey;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public WeKnoraClient(@Value("${dam.weknora.base-url:https://wiki-medical.cuhkszmc.cn/api/v1}") String baseUrl,
                         @Value("${dam.weknora.api-key:}") String apiKey) {
        this.baseUrl = StrUtil.removeSuffix(StrUtil.blankToDefault(baseUrl, "").trim(), "/");
        this.apiKey = StrUtil.blankToDefault(apiKey, "").trim();
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    public boolean isConfigured() {
        return StrUtil.isNotBlank(baseUrl) && StrUtil.isNotBlank(apiKey);
    }

    public String getBaseUrl() { return baseUrl; }

    public JsonNode createKnowledgeBase(String name, String description,
                                        String embeddingModelId, String summaryModelId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", "document");
        if (StrUtil.isNotBlank(description)) body.put("description", description);
        if (StrUtil.isNotBlank(embeddingModelId)) body.put("embedding_model_id", embeddingModelId);
        if (StrUtil.isNotBlank(summaryModelId)) body.put("summary_model_id", summaryModelId);
        return call(HttpMethod.POST, "/knowledge-bases", body);
    }

    public JsonNode getKnowledgeBase(String kbId) {
        return call(HttpMethod.GET, "/knowledge-bases/" + enc(kbId), null);
    }

    /** 给已有 KB 补默认模型；PATCH 只改传入字段 */
    public JsonNode bindDefaultModels(String kbId, String embeddingModelId, String summaryModelId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(embeddingModelId)) body.put("embedding_model_id", embeddingModelId);
        if (StrUtil.isNotBlank(summaryModelId)) body.put("summary_model_id", summaryModelId);
        return call(HttpMethod.PATCH, "/knowledge-bases/" + enc(kbId), body);
    }

    public List<JsonNode> listModels() {
        JsonNode data = call(HttpMethod.GET, "/models", null);
        List<JsonNode> result = new ArrayList<>();
        if (data != null && data.isArray()) data.forEach(result::add);
        return result;
    }

    public List<JsonNode> listKnowledgeBases() {
        JsonNode data = call(HttpMethod.GET, "/knowledge-bases?page=1&page_size=100", null);
        List<JsonNode> result = new ArrayList<>();
        if (data != null && data.isArray()) data.forEach(result::add);
        return result;
    }

    public List<JsonNode> listTags(String kbId) {
        JsonNode data = call(HttpMethod.GET, "/knowledge-bases/" + enc(kbId) + "/tags?page=1&page_size=100", null);
        List<JsonNode> result = new ArrayList<>();
        if (data != null && data.isArray()) data.forEach(result::add);
        return result;
    }

    public JsonNode createTag(String kbId, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        return call(HttpMethod.POST, "/knowledge-bases/" + enc(kbId) + "/tags", body);
    }

    public JsonNode createManual(String kbId, String title, String content, List<String> tagIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("status", "publish");
        if (tagIds != null && !tagIds.isEmpty()) body.put("tag_ids", tagIds);
        return call(HttpMethod.POST, "/knowledge-bases/" + enc(kbId) + "/knowledge/manual", body);
    }

    public JsonNode updateManual(String knowledgeId, String title, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("status", "publish");
        return call(HttpMethod.PUT, "/knowledge/manual/" + enc(knowledgeId), body);
    }

    /** 写入 DAM 来源元数据，便于反向定位 */
    public void updateCustomMetadata(String knowledgeId, Map<String, Object> metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("custom_metadata", metadata);
        call(HttpMethod.PUT, "/knowledge/" + enc(knowledgeId), body);
    }

    public void deleteKnowledge(String knowledgeId) {
        call(HttpMethod.DELETE, "/knowledge/" + enc(knowledgeId), null);
    }

    /** 外部检索：POST /knowledge-search */
    public List<JsonNode> search(String query, String kbId, int matchCount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("knowledge_base_ids", List.of(kbId));
        body.put("match_count", Math.max(1, Math.min(matchCount, 50)));
        JsonNode data = call(HttpMethod.POST, "/knowledge-search", body);
        List<JsonNode> result = new ArrayList<>();
        if (data != null && data.isArray()) data.forEach(result::add);
        return result;
    }

    public JsonNode getKnowledge(String knowledgeId) {
        return call(HttpMethod.GET, "/knowledge/" + enc(knowledgeId), null);
    }

    private JsonNode call(HttpMethod method, String path, Object body) {
        if (!isConfigured()) throw new BusinessException("WeKnora 知识库未配置，请设置 DAM_WEKNORA_API_KEY");
        try {
            RestClient.RequestBodySpec spec = restClient.method(method).uri(path)
                    .header("X-API-Key", apiKey)
                    .accept(MediaType.APPLICATION_JSON);
            if (body != null) spec.contentType(MediaType.APPLICATION_JSON).body(body);
            String json = spec.retrieve().body(String.class);
            if (StrUtil.isBlank(json)) return null;
            JsonNode root = mapper.readTree(json);
            if (root.has("success") && !root.path("success").asBoolean(false)) {
                throw new BusinessException("WeKnora 调用失败: " + root.path("error").path("message").asText(json));
            }
            return root.has("data") ? root.get("data") : root;
        } catch (RestClientResponseException e) {
            String msg = e.getResponseBodyAsString();
            log.warn("WeKnora {} {} 失败: {} {}", method, path, e.getStatusCode(), msg);
            throw new BusinessException("WeKnora 调用失败(" + e.getStatusCode().value() + "): " + abbreviate(msg));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WeKnora {} {} 异常: {}", method, path, e.getMessage());
            throw new BusinessException("WeKnora 调用失败: " + e.getMessage());
        }
    }

    private String enc(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
