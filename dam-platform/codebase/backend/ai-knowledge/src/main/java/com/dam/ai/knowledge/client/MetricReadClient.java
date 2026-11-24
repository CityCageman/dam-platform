package com.dam.ai.knowledge.client;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.dam.platform.common.exception.BusinessException;
import com.dam.ai.knowledge.dto.MetricDefinition;
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
 * 指标中心只读 HTTP 客户端。
 * <p>
 * 知识库在保存/展示“指标解释”时需要读取指标口径，但指标数据归 analytics-metric 独占，
 * 因此本服务不依赖 catalog 的 MetricDefinitionMapper，也不直连 {@code dam_metric} 表，
 * 而是调用 analytics-metric 的内部只读接口：
 * <ul>
 *   <li>{@code GET /api/metrics/internal/active?limit=500}</li>
 *   <li>{@code GET /api/metrics/{id}}</li>
 * </ul>
 * 服务不可用、鉴权失败或响应无法解析时抛出明确的 {@link BusinessException}，绝不返回伪造指标。
 *
 * @author DAM Platform
 */
@Slf4j
@Component
public class MetricReadClient {

    /**
     * 解析 analytics-metric 返回的 Result 与指标 DTO。
     * Spring Boot 4 使用 Jackson 3，内置 java.time 支持，可直接解析指标的 LocalDateTime 审计字段。
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** listActive 的最大条数，与 analytics-metric 内部接口上限保持一致 */
    private static final int MAX_LIMIT = 500;

    /** 指标服务基础地址，默认本机 8094，可通过 DAM_METRIC_URL 覆盖 */
    private final String baseUrl;

    /** 指标服务 HTTP 客户端 */
    private final RestClient restClient;

    /**
     * 构造指标只读客户端。
     *
     * @param baseUrl 指标服务基础地址
     */
    public MetricReadClient(
            @Value("${dam.metric-url:${DAM_METRIC_URL:http://127.0.0.1:8094}}") String baseUrl) {
        this.baseUrl = StrUtil.removeSuffix(
                StrUtil.blankToDefault(baseUrl, "http://127.0.0.1:8094").trim(), "/");
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    /**
     * 按 ID 查询指标。
     *
     * @param id 指标 ID
     * @return 指标定义；指标不存在时返回 {@code null}
     * @throws BusinessException 指标服务不可用、鉴权失败或响应解析失败
     */
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

    /**
     * 查询启用指标，最多 500 条。
     *
     * @param limit 期望条数
     * @return 指标列表
     * @throws BusinessException 指标服务不可用、鉴权失败或响应解析失败
     */
    public List<MetricDefinition> listActive(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        JsonNode data = fetch("/api/metrics/internal/active?limit={limit}", size, false);
        return convertList(data);
    }

    /**
     * 调用 analytics-metric 并解析统一 Result，返回其 data 节点。
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
            throw new BusinessException("指标服务不可用（" + baseUrl + "），请确认 analytics-metric 已启动：" + e.getMessage());
        }

        try {
            JsonNode root = MAPPER.readTree(StrUtil.blankToDefault(raw, "{}"));
            int code = root.path("code").asInt(200);
            if (code != 200) {
                String message = root.path("message").asString("");
                // analytics-metric 对不存在的指标返回业务错误，findById 需保留“不存在返回 null”的语义
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

    /**
     * 把 Result.data 数组转换为本地指标 DTO。
     *
     * @param data data 节点
     * @return 指标列表
     */
    private List<MetricDefinition> convertList(JsonNode data) {
        List<MetricDefinition> result = new ArrayList<>();
        if (data == null || data.isNull() || data.isMissingNode()) {
            return result;
        }
        if (!data.isArray()) {
            throw new BusinessException("指标服务响应格式错误（" + baseUrl + "）：data 不是数组");
        }
        for (JsonNode node : data) {
            if (node != null && !node.isNull()) {
                result.add(convert(node));
            }
        }
        return result;
    }

    /**
     * 把单个指标节点转换为本地 DTO。
     *
     * @param node 指标节点
     * @return 指标 DTO
     */
    private MetricDefinition convert(JsonNode node) {
        try {
            return MAPPER.treeToValue(node, MetricDefinition.class);
        } catch (Exception e) {
            throw new BusinessException("指标服务响应解析失败（" + baseUrl + "）：" + e.getMessage());
        }
    }

    /** 透传当前请求 token，便于 analytics-metric 侧做鉴权/审计 */
    private String token() {
        try {
            String token = SaHolder.getRequest().getHeader("token");
            if (StrUtil.isBlank(token)) {
                token = StpUtil.getTokenValue();
            }
            return StrUtil.blankToDefault(token, "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 截断错误响应，避免把超长外部报文写进业务异常。
     *
     * @param value 原始文本
     * @return 最多 500 字符
     */
    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
