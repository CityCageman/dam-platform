package com.dam.catalog.service;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * catalog 调用 dam-ai-workbench 任务中心。
 * catalog 不再直接读写 dam_ai.ai_task / ai_task_item。
 */
@Component
public class AiWorkbenchTaskClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${dam.ai-workbench-url:http://127.0.0.1:8090}")
    private String workbenchUrl;

    public void requireTask(Long taskId, String expectedType, String token) {
        if (taskId == null) throw new BusinessException("缺少任务ID");
        JsonNode data = get("/api/ai-workbench/internal/tasks/" + taskId, token);
        String actual = data.path("taskType").asText("");
        if (!expectedType.equalsIgnoreCase(actual)) {
            throw new BusinessException("任务类型不是 " + expectedType);
        }
    }

    public void addItem(Long taskId, String itemType, Long targetId, String targetName,
                        String draftContent, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemType", itemType);
        body.put("targetId", targetId);
        body.put("targetName", targetName);
        body.put("draftContent", draftContent);
        post("/api/ai-workbench/internal/tasks/" + taskId + "/items", body, token);
    }

    public void runAnnotate(Long taskId, Long dataSourceId, boolean pendingOnly, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        body.put("dataSourceId", dataSourceId);
        body.put("pendingOnly", pendingOnly);
        body.put("token", token);
        post("/api/ai-workbench/internal/run/annotate", body, token);
    }

    public void runClassify(Long taskId, Long dataSourceId, boolean pendingOnly, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        body.put("dataSourceId", dataSourceId);
        body.put("pendingOnly", pendingOnly);
        body.put("token", token);
        post("/api/ai-workbench/internal/run/classify", body, token);
    }

    private JsonNode get(String path, String token) {
        String raw = client().get().uri(path).header("token", tokenValue(token))
                .retrieve().body(String.class);
        return unwrap(raw);
    }

    private void post(String path, Map<String, Object> body, String token) {
        client().post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .header("token", tokenValue(token)).body(body)
                .retrieve().body(String.class);
    }

    private JsonNode unwrap(String raw) {
        try {
            JsonNode root = MAPPER.readTree(raw == null ? "{}" : raw);
            if (root.path("code").asInt(200) != 200) {
                throw new BusinessException(root.path("message").asText("workbench 调用失败"));
            }
            return root.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("workbench 响应解析失败: " + e.getMessage());
        }
    }

    private String tokenValue(String token) {
        if (token != null && !token.isBlank()) return token;
        try { return StpUtil.getTokenValue(); } catch (Exception e) { return ""; }
    }

    private RestClient client() {
        return RestClient.builder().baseUrl(workbenchUrl).build();
    }
}
