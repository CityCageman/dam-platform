package com.dam.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.dam.agent.dto.ChatCompletionResponse;
import com.dam.agent.dto.ChatRequest;
import com.dam.agent.service.AgentService;
import com.dam.common.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能体接口
 * <p>
 * 提供：
 * <ul>
 *   <li>流式对话（独立聊天窗口，SSE 返回）</li>
 *   <li>一次性完成（业务嵌入，如生成资产描述/工单摘要）</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 流式对话（独立聊天窗口）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        StpUtil.checkLogin();
        return agentService.streamChat(request);
    }

    /**
     * 一次性完成（业务嵌入）
     */
    @PostMapping("/complete")
    public Result<ChatCompletionResponse> complete(@Valid @RequestBody ChatRequest request) {
        StpUtil.checkLogin();
        return Result.success(agentService.complete(request));
    }
}
