package com.dam.platform.scheduler.service;

import com.dam.platform.scheduler.client.DolphinSchedulerClient;
import com.dam.platform.scheduler.config.DolphinSchedulerProperties;
import com.dam.platform.scheduler.dto.dolphin.DolphinProbeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 调度执行引擎状态查询与 DolphinScheduler 连通性测试。
 * <p>
 * 状态接口只暴露引擎类型、DS 开关/配置是否完整以及最近一次探测结果；base-url、token
 * 等敏感配置永远不会出现在响应中。测试接口只使用部署配置，不接受调用方传入 URL，
 * 通过拉取流程定义列表验证 base-url/token/project-code 三项配置可用。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class DolphinEngineService {

    /** DS 配置 */
    private final DolphinSchedulerProperties properties;

    /** DS 客户端 */
    private final DolphinSchedulerClient dolphinClient;

    /** 最近一次探测结果（未探测时为 null） */
    private final AtomicReference<Map<String, Object>> lastProbe = new AtomicReference<>();

    /**
     * 查询当前执行引擎状态。
     *
     * @return engine、dolphin.enabled/configured、lastProbe
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("engine", properties.resolvedEngine());

        DolphinSchedulerProperties.Dolphin dolphin = properties.getDolphin();
        List<String> issues = properties.configurationIssues();
        Map<String, Object> dolphinStatus = new LinkedHashMap<>();
        dolphinStatus.put("enabled", properties.isDolphinEnabled());
        dolphinStatus.put("configured", properties.isDolphinConfigured());
        dolphinStatus.put("clientReady", dolphinClient.isConfigured());
        // configurationIssues 只含字段名与原因，不含 token/base-url 实际值
        dolphinStatus.put("configurationIssues", issues);
        dolphinStatus.put("tenantConfigured", dolphin != null
                && StringUtils.hasText(dolphin.getTenantCode()));
        dolphinStatus.put("pollIntervalMs", dolphin == null ? null : dolphin.getPollIntervalMs());
        dolphinStatus.put("pollBatchSize", dolphin == null ? null : dolphin.getPollBatchSize());
        status.put("dolphin", dolphinStatus);

        status.put("lastProbe", lastProbe.get());
        return status;
    }

    /**
     * 使用部署配置测试 DolphinScheduler 连通性。
     * <p>
     * 不触发流程实例，只拉取流程定义列表；结果写入 lastProbe 供状态接口展示。
     * 未配置时返回 ok=false 与缺失字段说明，不抛出异常。
     *
     * @return ok/message/checkedAt/definitionCount
     */
    public Map<String, Object> testConnection() {
        DolphinProbeResult result = dolphinClient.probe();

        Map<String, Object> probe = new LinkedHashMap<>();
        probe.put("ok", result.ok());
        probe.put("message", result.message());
        probe.put("checkedAt", LocalDateTime.now());
        probe.put("definitionCount", result.definitionCount());
        lastProbe.set(Collections.unmodifiableMap(probe));

        return new LinkedHashMap<>(probe);
    }
}
