package com.dam.agent.service;

import com.dam.agent.config.AgentConfigProperties;
import com.dam.agent.config.AgentProperties;
import com.dam.agent.dto.ChatCompletionResponse;
import com.dam.agent.dto.ChatRequest;
import com.dam.common.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 智能体业务服务
 * <p>
 * 流式对话通过 SseEmitter 转发上游增量；一次性完成供业务嵌入调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentClient agentClient;
    private final AgentProperties properties;

    /** 流式转发线程池（阻塞式 HTTP 调用在独立线程执行，避免占用 Servlet 线程） */
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    /**
     * 流式对话，SSE 事件约定：
     * <ul>
     *   <li>{@code {"reasoning":"思考文本"}} 思考过程增量</li>
     *   <li>{@code {"delta":"文本片段"}} 最终结论增量</li>
     *   <li>{@code {"done":true}} 正常结束</li>
     *   <li>{@code {"error":"错误信息"}} 出错</li>
     * </ul>
     */
    public SseEmitter streamChat(ChatRequest request) {
        AgentConfigProperties config = properties.of(request.getAgentType());
        long timeoutMs = config.getTimeoutSeconds() * 1000L + 10_000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);

        streamExecutor.submit(() -> {
            try {
                agentClient.streamChat(
                        config, request.getMessages(), request.getModel(), request.getTemperature(), request.getMaxTokens(),
                        reasoning -> send(emitter, Map.of("reasoning", reasoning)),
                        delta -> send(emitter, Map.of("delta", delta)),
                        error -> send(emitter, Map.of("error", error)));
                send(emitter, Map.of("done", true));
                emitter.complete();
            } catch (Exception e) {
                log.error("智能体流式调用失败", e);
                try {
                    send(emitter, Map.of("error", "智能体调用失败: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {
                    // 客户端已断开连接，忽略
                }
            }
        });
        return emitter;
    }

    /**
     * 一次性完成（业务嵌入：生成资产描述、工单摘要等）
     */
    public ChatCompletionResponse complete(ChatRequest request) {
        AgentConfigProperties config = properties.of(request.getAgentType());
        try {
            return agentClient.complete(config, request.getMessages(), request.getModel(), request.getTemperature(), request.getMaxTokens());
        } catch (Exception e) {
            log.error("智能体一次性调用失败", e);
            throw new BusinessException("智能体调用失败: " + e.getMessage());
        }
    }

    /** 发送一个 SSE 事件，失败（客户端断开）时抛异常以终止上游读取 */
    private void send(SseEmitter emitter, Map<String, ?> data) {
        try {
            emitter.send(data);
        } catch (Exception e) {
            throw new IllegalStateException("SSE 发送失败", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdownNow();
    }
}
