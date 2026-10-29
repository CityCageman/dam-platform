package com.dam.agent.service;

import com.dam.agent.config.AgentConfigProperties;
import com.dam.agent.dto.ChatCompletionResponse;
import com.dam.agent.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 智能体 HTTP 客户端（OpenAI 兼容 /v1/chat/completions）
 * <p>
 * java.net.http.HttpClient 线程安全、可复用，作为类级单例复用底层连接。
 * 密钥仅在此处拼入请求头，日志不打印密钥与完整报文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 流式对话：逐段回调增量内容
     *
     * @param onReasoning 收到思考过程增量（reasoning_content）时回调
     * @param onDelta     收到最终结论增量（content）时回调
     * @param onError     上游返回异常（非 200）时回调
     */
    public void streamChat(AgentConfigProperties config, List<ChatMessage> messages, String model, Double temperature, Integer maxTokens,
                           Consumer<String> onReasoning, Consumer<String> onDelta, Consumer<String> onError) throws Exception {
        Map<String, Object> body = buildBody(config, messages, model, temperature, maxTokens);
        body.put("stream", true);

        HttpRequest request = buildRequest(config, body);
        HttpResponse<Stream<String>> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofLines());

        if (resp.statusCode() != 200) {
            onError.accept("智能体服务返回异常: HTTP " + resp.statusCode());
            return;
        }

        try (Stream<String> lines = resp.body()) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (line == null || line.isBlank()) continue;
                String data = line;
                if (data.startsWith("data:")) data = data.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                try {
                    JsonNode node = MAPPER.readTree(data);
                    JsonNode choices = node.path("choices");
                    if (choices.isArray() && !choices.isEmpty()) {
                        JsonNode delta = choices.get(0).path("delta");
                        // 思考过程（reasoning_content，DeepSeek-R1 / Qwen3 thinking 模式；兼容 reasoning 字段）
                        String reasoning = delta.path("reasoning_content").asText(null);
                        if (reasoning == null || reasoning.isEmpty()) {
                            reasoning = delta.path("reasoning").asText(null);
                        }
                        if (reasoning != null && !reasoning.isEmpty()) {
                            onReasoning.accept(reasoning);
                        }
                        // 最终结论（content）
                        String content = delta.path("content").asText(null);
                        if (content != null && !content.isEmpty()) {
                            onDelta.accept(content);
                        }
                    }
                } catch (Exception e) {
                    log.debug("忽略无法解析的 SSE 数据: {}", data);
                }
            }
        }
    }

    /**
     * 一次性完成（业务嵌入场景）
     */
    public ChatCompletionResponse complete(AgentConfigProperties config, List<ChatMessage> messages, String model, Double temperature, Integer maxTokens) throws Exception {
        Map<String, Object> body = buildBody(config, messages, model, temperature, maxTokens);
        body.put("stream", false);

        HttpRequest request = buildRequest(config, body);
        HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            String err = resp.body();
            String detail = err == null ? "" : (err.length() > 200 ? err.substring(0, 200) : err);
            throw new IllegalStateException("智能体服务返回异常: HTTP " + resp.statusCode() + " " + detail);
        }

        JsonNode node = MAPPER.readTree(resp.body());
        ChatCompletionResponse result = new ChatCompletionResponse();
        result.setModel(node.path("model").asText(null));
        JsonNode choices = node.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            result.setContent(choices.get(0).path("message").path("content").asText(null));
            result.setFinishReason(choices.get(0).path("finish_reason").asText(null));
        }
        JsonNode usage = node.path("usage");
        if (usage.isObject()) {
            result.setPromptTokens(usage.path("prompt_tokens").asInt(-1));
            result.setCompletionTokens(usage.path("completion_tokens").asInt(-1));
            result.setTotalTokens(usage.path("total_tokens").asInt(-1));
        }
        return result;
    }

    /** 组装请求体 */
    private Map<String, Object> buildBody(AgentConfigProperties config, List<ChatMessage> messages, String model, Double temperature, Integer maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", (model != null && !model.isBlank()) ? model : config.getModel());
        List<Map<String, Object>> msgs = messages.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", m.getRole());
            map.put("content", m.getContent());
            return map;
        }).toList();
        body.put("messages", msgs);
        if (temperature != null) body.put("temperature", temperature);
        if (maxTokens != null) body.put("max_tokens", maxTokens);
        return body;
    }

    /** 组装 HTTP 请求（认证头：Authorization: Bearer <apiKey>） */
    private HttpRequest buildRequest(AgentConfigProperties config, Map<String, Object> body) throws Exception {
        String json = MAPPER.writeValueAsString(body);
        return HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl()))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
