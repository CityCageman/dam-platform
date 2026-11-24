package com.dam.asset.shared.adapter;

import cn.dev33.satoken.context.SaHolder;
import cn.hutool.core.util.StrUtil;
import com.dam.asset.metric.entity.MetricDefinition;
import com.dam.asset.shared.port.MetricLookupPort;
import com.dam.platform.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * MetricLookupPort 的 HTTP 适配器。
 * <p>
 * 指标数据由 dam-metric 服务独占，catalog 不再直接访问 dam_metric 表；
 * 本适配器通过 dam-metric 的内部只读接口获取指标，并解析统一 {@code Result.data}。
 * 服务不可用或返回非成功状态码时抛出 {@link BusinessException}，绝不返回伪造/空指标掩盖故障。
 *
 * @author DAM Platform
 */
@Slf4j
@Component
public class MetricLookupHttpAdapter implements MetricLookupPort {

    /**
     * 解析 dam-metric 返回的 Result 与指标 DTO。
     * Spring Boot 4 使用 Jackson 3，内置 java.time 支持，可直接解析指标的 LocalDateTime 审计字段。
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** listActive 的最大条数，与 dam-metric 内部接口上限保持一致 */
    private static final int MAX_LIMIT = 500;

    private final String baseUrl;
    private final RestClient restClient;

    public MetricLookupHttpAdapter(
            @Value("${dam.metric-url:${DAM_METRIC_URL:http://127.0.0.1:8094}}") String baseUrl) {
        this.baseUrl = StrUtil.removeSuffix(
                StrUtil.blankToDefault(baseUrl, "http://127.0.0.1:8094").trim(), "/");
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    @Override
    public MetricDefinition findById(Long id) {
        if (id == null) {
            return null;
        }
        JsonNode data = fetch("/api/metrics/{id}", id, true);
        if (data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        return convert(data);
    }

    @Override
    public List<MetricDefinition> listActive(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        JsonNode data = fetch("/api/metrics/internal/active?limit={limit}", size, false);
        return convertList(data);
    }

    @Override
    public List<MetricDefinition> listActiveByDataSource(Long dataSourceId) {
        if (dataSourceId == null) {
            return List.of();
        }
        JsonNode data = fetch("/api/metrics/internal/by-data-source/{dataSourceId}", dataSourceId, false);
        return convertList(data);
    }

    /**
     * 调用 dam-metric 并解析统一 Result，返回其 data 节点。
     *
     * @param path          相对路径（可含 URI 模板变量）
     * @param uriVariable   模板变量值
     * @param allowNotFound 是否把“指标不存在”视为 null（findById 语义）
     * @return Result.data；不存在且 allowNotFound 时返回 null
     */
    private JsonNode fetch(String path, Object uriVariable, boolean allowNotFound) {
        String raw;
        try {
            raw = restClient.get().uri(path, uriVariable)
                    .header("token", token())
                    .retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            if (allowNotFound && e.getStatusCode().value() == 404) {
                return null;
            }
            log.warn("调用指标服务 {} 失败: HTTP {} {}", path, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException("指标服务调用失败（" + baseUrl + "）：HTTP "
                    + e.getStatusCode().value() + " " + abbreviate(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.warn("指标服务 {} 不可用: {}", baseUrl, e.getMessage());
            throw new BusinessException("指标服务不可用（" + baseUrl + "），请确认 dam-metric 已启动：" + e.getMessage());
        }

        try {
            JsonNode root = MAPPER.readTree(StrUtil.blankToDefault(raw, "{}"));
            int code = root.path("code").asInt(200);
            if (code != 200) {
                String message = root.path("message").asText("");
                // dam-metric 对不存在的指标会返回业务错误，findById 需保留“不存在返回 null”的端口语义
                if (allowNotFound && (code == 404 || message.contains("不存在"))) {
                    return null;
                }
                throw new BusinessException("指标服务调用失败（" + baseUrl + "）："
                        + StrUtil.blankToDefault(message, "未知错误"));
            }
            return root.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("指标服务响应解析失败（" + baseUrl + "）：" + e.getMessage());
        }
    }

    private List<MetricDefinition> convertList(JsonNode data) {
        List<MetricDefinition> result = new ArrayList<>();
        if (data == null || !data.isArray()) {
            return result;
        }
        for (JsonNode node : data) {
            if (node != null && !node.isNull()) {
                result.add(convert(node));
            }
        }
        return result;
    }

    private MetricDefinition convert(JsonNode node) {
        try {
            return MAPPER.treeToValue(node, MetricDefinition.class);
        } catch (Exception e) {
            throw new BusinessException("指标服务响应解析失败（" + baseUrl + "）：" + e.getMessage());
        }
    }

    /** 透传当前请求 token，便于 dam-metric 侧做鉴权/审计 */
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
