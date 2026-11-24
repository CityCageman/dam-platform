package com.dam.analytics.metric.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dam.platform.common.exception.BusinessException;
import com.dam.analytics.metric.entity.MetricDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * catalog 指标 AI 能力客户端。
 * <p>
 * analytics-metric 拆出后不再依赖 catalog 的 ReportService，也不再访问 catalog 的数据源/表结构表。
 * 指标 SQL 生成统一调用 catalog 的内部 AI 工具接口：
 * <pre>
 * POST ${DAM_CATALOG_URL:http://127.0.0.1:8092}/api/catalog/internal/ai/metrics/sql
 * body: 指标对象；header: token（浏览器 token 原样透传，catalog 据此做登录校验）
 * 响应: 统一 Result，取 data.sql
 * </pre>
 * catalog 负责模型调用、表结构检索与 SQL 白名单校验；本服务只负责把生成结果写回指标。
 *
 * @author DAM Platform
 */
@Slf4j
@Component
public class CatalogMetricAiClient {

    /** 指标 SQL 生成接口（catalog 内部接口，不直接暴露给浏览器） */
    private static final String METRIC_SQL_PATH = "/api/catalog/internal/ai/metrics/sql";

    /** catalog 服务根地址，末尾不带斜杠 */
    private final String catalogUrl;

    /**
     * 构造 catalog 内部 AI 客户端。
     *
     * @param catalogUrl catalog 服务地址，默认 127.0.0.1:8092，可用 DAM_CATALOG_URL 覆盖
     */
    public CatalogMetricAiClient(
            @Value("${dam.catalog-url:${DAM_CATALOG_URL:http://127.0.0.1:8092}}") String catalogUrl) {
        String url = StrUtil.blankToDefault(catalogUrl, "http://127.0.0.1:8092").trim();
        this.catalogUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 调用 catalog 内部接口，根据指标定义/公式生成指标 SQL。
     *
     * @param metric 指标定义，作为请求体传给 catalog
     * @param token  当前登录 token，原样透传到 catalog 的 token 请求头
     * @return catalog 返回的 data（包含 sql、tablesUsed、params 等字段）
     */
    public Map<String, Object> generateMetricSql(MetricDefinition metric, String token) {
        if (metric == null) {
            throw new BusinessException("指标不存在");
        }
        String raw;
        try {
            raw = RestClient.builder().baseUrl(catalogUrl).build()
                    .post().uri(METRIC_SQL_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("token", token == null ? "" : token)
                    .body(JSONUtil.toJsonStr(metricPayload(metric)))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("调用 catalog 指标 AI 接口失败: url={}{}, error={}", catalogUrl, METRIC_SQL_PATH, e.getMessage());
            throw new BusinessException("调用 catalog 指标 AI 接口失败: " + e.getMessage());
        }

        try {
            JSONObject root = JSONUtil.parseObj(StrUtil.blankToDefault(raw, "{}"));
            int code = root.getInt("code", 200);
            if (code != 200) {
                throw new BusinessException(code,
                        root.getStr("message", "catalog 指标 AI 接口返回失败"));
            }
            JSONObject data = root.getJSONObject("data");
            if (data == null) {
                throw new BusinessException("catalog 未返回指标 SQL 数据");
            }
            String sql = data.getStr("sql");
            if (StrUtil.isBlank(sql)) {
                throw new BusinessException("AI 未生成有效 SQL");
            }
            // JSONObject 本身是 Map，复制一份避免把 Hutool 的 JSON 配置对象带到业务层
            return new LinkedHashMap<>(data);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析 catalog 指标 AI 响应失败: {}", e.getMessage());
            throw new BusinessException("catalog 指标 AI 响应解析失败: " + e.getMessage());
        }
    }

    /**
     * 构造传给 catalog 的指标对象。
     * <p>
     * 只保留业务字段，不携带 createdAt/updatedAt/deletedAt 等审计字段：catalog 侧通常以
     * Map 或指标 DTO 接收，省略审计时间可避免不同 JSON 时间序列化方式带来的兼容问题。
     *
     * @param metric 指标定义
     * @return 指标业务字段（JSONB 字段保持原始 JSON 字符串）
     */
    private Map<String, Object> metricPayload(MetricDefinition metric) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", metric.getId());
        payload.put("metricCode", metric.getMetricCode());
        payload.put("metricName", metric.getMetricName());
        payload.put("metricType", metric.getMetricType());
        payload.put("businessDomain", metric.getBusinessDomain());
        payload.put("definition", metric.getDefinition());
        payload.put("formula", metric.getFormula());
        payload.put("unit", metric.getUnit());
        payload.put("dataSourceId", metric.getDataSourceId());
        payload.put("sourceTables", metric.getSourceTables());
        payload.put("sqlExpression", metric.getSqlExpression());
        payload.put("reportSpec", metric.getReportSpec());
        payload.put("dimensions", metric.getDimensions());
        payload.put("timeGrain", metric.getTimeGrain());
        payload.put("owner", metric.getOwner());
        payload.put("status", metric.getStatus());
        payload.put("version", metric.getVersion());
        return payload;
    }
}
