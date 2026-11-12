package com.dam.aiworkbench.controller;

import com.dam.common.vo.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 工作台基础状态接口。
 * <p>
 * 后续对话、任务、报表生成等编排接口统一挂在 /api/ai-workbench 下。
 */
@RestController
@RequestMapping("/api/ai-workbench")
public class AiWorkbenchController {

    @Value("${dam.ai-workbench.model-gateway-url:http://127.0.0.1:8089}")
    private String modelGatewayUrl;

    @Value("${dam.ai-workbench.catalog-url:http://127.0.0.1:8092}")
    private String catalogUrl;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "dam-ai-workbench");
        result.put("status", "UP");
        result.put("role", "AI orchestration");
        result.put("modelGatewayUrl", modelGatewayUrl);
        result.put("catalogUrl", catalogUrl);
        return Result.success(result);
    }
}
