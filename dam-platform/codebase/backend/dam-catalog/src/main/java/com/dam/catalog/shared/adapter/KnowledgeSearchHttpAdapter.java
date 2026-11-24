package com.dam.catalog.shared.adapter;

import cn.dev33.satoken.context.SaHolder;
import cn.hutool.core.util.StrUtil;
import com.dam.catalog.shared.port.KnowledgeSearchPort;
import com.dam.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KnowledgeSearchPort 的 HTTP 适配器。
 * <p>
 * 知识库由 ai-knowledge 服务独占，catalog 不再包含知识库实现，也不直接访问 dam_knowledge 表；
 * 本适配器通过 ai-knowledge 的外部检索接口
 * {@code POST /api/knowledge/external/search} 获取知识条目，并解析统一 {@code Result.data}。
 * 服务不可用或返回非 200 时抛出 {@link BusinessException}，由调用方决定是否降级。
 *
 * @author DAM Platform
 */
@Slf4j
@Component
public class KnowledgeSearchHttpAdapter implements KnowledgeSearchPort {

    /** ai-knowledge 统一 Result / 检索结果解析（Spring Boot 4 使用 Jackson 3） */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 与 ai-knowledge 检索接口的 limit 上限保持一致 */
    private static final int MAX_LIMIT = 50;

    private final String baseUrl;
    private final RestClient restClient;

    public KnowledgeSearchHttpAdapter(
            @Value("${dam.knowledge-url:${DAM_KNOWLEDGE_URL:http://127.0.0.1:8098}}") String baseUrl) {
        this.baseUrl = StrUtil.removeSuffix(
                StrUtil.blankToDefault(baseUrl, "http://127.0.0.1:8098").trim(), "/");
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    @Override
    public Map<String, Object> search(String query, int limit) {
        if (StrUtil.isBlank(query)) {
            throw new BusinessException("知识检索内容不能为空");
        }
        int topK = Math.max(1, Math.min(limit, MAX_LIMIT));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("limit", topK);

        String raw;
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri("/api/knowledge/external/search")
                    .header("Content-Type", "application/json");
            String token = token();
            if (StrUtil.isNotBlank(token)) {
                request.header("token", token);
            }
            raw = request.body(body).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("调用知识服务 /api/knowledge/external/search 失败: HTTP {} {}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException("知识服务调用失败（" + baseUrl + "）：HTTP "
                    + e.getStatusCode().value() + " " + abbreviate(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.warn("知识服务 {} 不可用: {}", baseUrl, e.getMessage());
            throw new BusinessException("知识服务不可用（" + baseUrl + "），请确认 ai-knowledge 已启动：" + e.getMessage());
        }

        try {
            JsonNode root = MAPPER.readTree(StrUtil.blankToDefault(raw, "{}"));
            if (root == null || root.isNull() || root.isMissingNode()) {
                throw new BusinessException("知识服务返回空响应（" + baseUrl + "）");
            }
            JsonNode data;
            if (root.has("code")) {
                int code = root.path("code").asInt(200);
                if (code != 200) {
                    String message = root.path("message").asText("");
                    throw new BusinessException("知识服务调用失败（" + baseUrl + "）："
                            + StrUtil.blankToDefault(message, "未知错误"));
                }
                data = root.path("data");
            } else {
                // 兼容直接返回检索结果（无 Result 包装）的服务实现
                data = root;
            }
            return toResultMap(data);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("知识服务响应解析失败（" + baseUrl + "）：" + e.getMessage());
        }
    }

    /** 把 Result.data 转为保持 {@code results} 结构的 Map，避免调用方拿不到检索列表 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toResultMap(JsonNode data) {
        if (data == null || data.isNull() || data.isMissingNode()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("results", List.of());
            return empty;
        }
        Map<String, Object> result;
        if (data.isArray()) {
            result = new LinkedHashMap<>();
            result.put("results", MAPPER.convertValue(data, List.class));
            return result;
        }
        if (!data.isObject()) {
            throw new BusinessException("知识服务响应解析失败（" + baseUrl + "）：检索结果不是对象");
        }
        result = MAPPER.convertValue(data, Map.class);
        if (!(result.get("results") instanceof List<?>)) {
            result.put("results", List.of());
        }
        return result;
    }

    /** 透传当前请求 token，便于 ai-knowledge 侧做鉴权/审计 */
    private String token() {
        try {
            return StrUtil.blankToDefault(SaHolder.getRequest().getHeader("token"), "");
        } catch (Exception e) {
            return "";
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
